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
    private val sharedPreferences = ServiceManager.getService()?.getSharedPreferences("me.kavishdevar.aln", Context.MODE_PRIVATE)
    private val offListeningModeEnabled = sharedPreferences?.getBoolean("off_listening_mode", false) == true
    private val ancModes = if (offListeningModeEnabled) listOf(NoiseControlMode.OFF.name, NoiseControlMode.NOISE_CANCELLATION.name, NoiseControlMode.TRANSPARENCY.name, NoiseControlMode.ADAPTIVE.name) else listOf(NoiseControlMode.NOISE_CANCELLATION.name, NoiseControlMode.TRANSPARENCY.name, NoiseControlMode.ADAPTIVE.name)
    private var currentModeIndex = if (offListeningModeEnabled) 3 else 2
    private lateinit var ancStatusReceiver: BroadcastReceiver
    private lateinit var availabilityReceiver: BroadcastReceiver

    @SuppressLint("InlinedApi")
    override fun onStartListening() {
        Log.d("AirPodsQSService", "off mode: $offListeningModeEnabled")
        super.onStartListening()
        currentModeIndex = (ServiceManager.getService()?.getANC()?.minus(if (offListeningModeEnabled) 1 else 2)) ?: if (offListeningModeEnabled) 3 else 2
        if (currentModeIndex == -1) {
            currentModeIndex = if (offListeningModeEnabled) 3 else 2
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
                currentModeIndex = ancStatus - if (offListeningModeEnabled) 1 else 2
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
        Log.d("QuickSettingTileService", "Current mode index: $currentModeIndex, ancModes size: ${ancModes.size}")
        currentModeIndex = (currentModeIndex + 1) % ancModes.size
        Log.d("QuickSettingTileService", "New mode index: $currentModeIndex")
        switchAncMode(if (offListeningModeEnabled) currentModeIndex + 1 else currentModeIndex + 2)
    }

    private fun updateTile() {
        val currentMode = ancModes[currentModeIndex % ancModes.size]
        qsTile.label = currentMode.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.updateTile()
    }

    private fun switchAncMode(modeIndex: Int) {
        currentModeIndex = if (offListeningModeEnabled) modeIndex else modeIndex - 1
        val airPodsService = ServiceManager.getService()
        airPodsService?.setANCMode(if (offListeningModeEnabled) modeIndex + 1 else modeIndex)
        updateTile()
    }
}