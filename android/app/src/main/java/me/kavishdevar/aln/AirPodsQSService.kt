package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log

class AirPodsQSService: TileService() {
    private val ancModes = listOf(NoiseControlMode.NOISE_CANCELLATION.name, NoiseControlMode.TRANSPARENCY.name, NoiseControlMode.ADAPTIVE.name)
    private var currentModeIndex = 2
    private lateinit var ancStatusReceiver: BroadcastReceiver
    private lateinit var availabilityReceiver: BroadcastReceiver

    @SuppressLint("InlinedApi")
    override fun onStartListening() {
        super.onStartListening()
        currentModeIndex = (ServiceManager.getService()?.getANC()?.minus(1)) ?: 3
        if (currentModeIndex == -1) {
            currentModeIndex = 3
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
                currentModeIndex = ancStatus - 1
                updateTile()
            }
        }

        availabilityReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AirPodsNotifications.AIRPODS_CONNECTED) {
                    qsTile.state = Tile.STATE_ACTIVE
                    qsTile.updateTile()
                }
                else if (intent.action == AirPodsNotifications.AIRPODS_DISCONNECTED) {
                    qsTile.state = Tile.STATE_UNAVAILABLE
                    qsTile.updateTile()
                }
            }
        }

        registerReceiver(ancStatusReceiver, IntentFilter(AirPodsNotifications.ANC_DATA), RECEIVER_EXPORTED)
        updateTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        try {
            unregisterReceiver(ancStatusReceiver)
        }
        catch (
            e: IllegalArgumentException
        )
        {
            Log.e("QuickSettingTileService", "Receiver not registered")
        }
        try {
            unregisterReceiver(availabilityReceiver)
        }
        catch (
            e: IllegalArgumentException
        )
        {
            Log.e("QuickSettingTileService", "Receiver not registered")
        }
    }

    override fun onClick() {
        super.onClick()
        Log.d("QuickSettingTileService", "ANC tile clicked")
        currentModeIndex = (currentModeIndex + 1) % ancModes.size
        switchAncMode(currentModeIndex)
    }

    private fun updateTile() {
        val currentMode = ancModes[currentModeIndex]
        qsTile.label = currentMode.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.updateTile()
    }

    private fun switchAncMode(modeIndex: Int) {
        currentModeIndex = modeIndex
        val airPodsService = ServiceManager.getService()
        airPodsService?.setANCMode(currentModeIndex + 1)
        updateTile()
    }
}