package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.ParcelUuid

class StartupReceiver : BroadcastReceiver() {

    companion object {
        val PodsUUIDS: Set<ParcelUuid> = setOf(
            ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a"),
            ParcelUuid.fromString("2a72e02b-7b99-778f-014d-ad0b7221ec74")
        )

        val btActions: Set<String> = setOf(
            BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED,
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED,
            BluetoothDevice.ACTION_BOND_STATE_CHANGED,
            BluetoothDevice.ACTION_NAME_CHANGED
        )
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return

        intent.action?.let { action ->
            if (btActions.contains(action)) {
                try {
                    val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothAdapter.ERROR)
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        btProfileChanges(context, state, it)
                    }
                } catch (e: NullPointerException) {
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun isPods(device: BluetoothDevice): Boolean {
        device.uuids?.forEach { uuid ->
            if (PodsUUIDS.contains(uuid)) {
                return true
            }
        }
        return false
    }

    private fun startPodsService(context: Context, device: BluetoothDevice) {
        if (!isPods(device)) return
        val intent = Intent(context, AirPodsService::class.java).apply {
            putExtra(BluetoothDevice.EXTRA_DEVICE, device)
        }
        context.startService(intent)
    }

    private fun stopPodsService(context: Context) {
        context.stopService(Intent(context, AirPodsService::class.java))
    }

    private fun btProfileChanges(context: Context, state: Int, device: BluetoothDevice) {
        when (state) {
            BluetoothProfile.STATE_CONNECTED -> startPodsService(context, device)
            BluetoothProfile.STATE_DISCONNECTED, BluetoothProfile.STATE_DISCONNECTING -> stopPodsService(context)
        }
    }
}