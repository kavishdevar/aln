/*
 * AirPods like Normal (ALN) - Bringing Apple-only features to Linux and Android for seamless AirPods functionality!
 * 
 * Copyright (C) 2024 Kavish Devar
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package me.kavishdevar.aln.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import me.kavishdevar.aln.utils.AirPodsNotifications
import me.kavishdevar.aln.utils.NoiseControlMode

class AirPodsQSService: TileService() {
    private val ancModes = listOf(NoiseControlMode.NOISE_CANCELLATION.name, NoiseControlMode.TRANSPARENCY.name, NoiseControlMode.ADAPTIVE.name)
    private var currentModeIndex = 2
    private lateinit var ancStatusReceiver: BroadcastReceiver
    private lateinit var availabilityReceiver: BroadcastReceiver

    @SuppressLint("InlinedApi")
    override fun onStartListening() {
        super.onStartListening()
        currentModeIndex = (ServiceManager.getService()?.getANC()?.minus(1)) ?: -1
        if (currentModeIndex == -1) {
            currentModeIndex = 2
        }

        if (ServiceManager.getService() == null) {
            qsTile.state = Tile.STATE_UNAVAILABLE
            qsTile.updateTile()
        }
        if (ServiceManager.getService()?.isConnected == true) {
            qsTile.state = Tile.STATE_ACTIVE
            qsTile.updateTile()
        }
        else {
            qsTile.state = Tile.STATE_UNAVAILABLE
            qsTile.updateTile()
        }

        ancStatusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val ancStatus = intent.getIntExtra("data", 4)
                currentModeIndex = if (ancStatus == 2) 0 else if (ancStatus == 3) 1 else if (ancStatus == 4) 2 else 2
                updateTile()
            }
        }

        availabilityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AirPodsNotifications.Companion.AIRPODS_CONNECTED) {
                    qsTile.state = Tile.STATE_ACTIVE
                    qsTile.updateTile()
                }
                else if (intent.action == AirPodsNotifications.Companion.AIRPODS_DISCONNECTED) {
                    qsTile.state = Tile.STATE_UNAVAILABLE
                    qsTile.updateTile()
                }
            }
        }

        registerReceiver(ancStatusReceiver,
            IntentFilter(AirPodsNotifications.Companion.ANC_DATA), RECEIVER_EXPORTED)

        qsTile.state = if (ServiceManager.getService()?.isConnected == true) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
        val ancIndex = ServiceManager.getService()?.getANC()
        currentModeIndex = if (ancIndex != null) { if (ancIndex == 2) 0 else if (ancIndex == 3) 1 else if (ancIndex == 4) 2 else 2 } else 0
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        try {
            unregisterReceiver(ancStatusReceiver)
        }
        catch (
            _: IllegalArgumentException
        )
        {
            Log.e("QuickSettingTileService", "Receiver not registered")
        }
        try {
            unregisterReceiver(availabilityReceiver)
        }
        catch (
            _: IllegalArgumentException
        )
        {
            Log.e("QuickSettingTileService", "Receiver not registered")
        }
    }

    override fun onClick() {
        super.onClick()
        Log.d("QuickSettingTileService", "ANC tile clicked")
        currentModeIndex = (currentModeIndex + 1) % ancModes.size
        Log.d("QuickSettingTileService", "New mode index: $currentModeIndex, would be set to ${currentModeIndex + 1}")
        switchAncMode()
    }

    private fun updateTile() {
        val currentMode = ancModes[currentModeIndex % ancModes.size]
        qsTile.label = currentMode.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.updateTile()
    }

    private fun switchAncMode() {
        val airPodsService = ServiceManager.getService()
        Log.d("QuickSettingTileService", "Setting ANC mode to ${currentModeIndex + 2}")
        airPodsService?.setANCMode(currentModeIndex + 2)
        Log.d("QuickSettingTileService", "ANC mode set to ${currentModeIndex + 2}")
        updateTile()
    }
}