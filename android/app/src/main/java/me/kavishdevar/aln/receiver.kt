package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

class BluetoothReceiver : BroadcastReceiver() {
    fun onConnect(bluetoothDevice: BluetoothDevice?) {

    }

    fun onDisconnect(bluetoothDevice: BluetoothDevice?) {

    }

    @SuppressLint("NewApi")
    override fun onReceive(context: Context?, intent: Intent) {
        val bluetoothDevice =
            intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE", BluetoothDevice::class.java)
        val action = intent.action

        // Airpods filter
        if (bluetoothDevice != null && action != null && !action.isEmpty()) {
            // Airpods connected, show notification.
            if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
                onConnect(bluetoothDevice)
            }

            // Airpods disconnected, remove notification but leave the scanner going.
            if (BluetoothDevice.ACTION_ACL_DISCONNECTED == action
                || BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED == action
            ) {
                onDisconnect(bluetoothDevice)
            }
        }
    }

    companion object {
        /**
         * When the service is created, we register to get as many bluetooth and airpods related events as possible.
         * ACL_CONNECTED and ACL_DISCONNECTED should have been enough, but you never know with android these days.
         */
        fun buildFilter(): IntentFilter {
            val intentFilter = IntentFilter()
            intentFilter.addAction("android.bluetooth.device.action.ACL_CONNECTED")
            intentFilter.addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
            intentFilter.addAction("android.bluetooth.device.action.BOND_STATE_CHANGED")
            intentFilter.addAction("android.bluetooth.device.action.NAME_CHANGED")
            intentFilter.addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED")
            intentFilter.addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            intentFilter.addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
            intentFilter.addAction("android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT")
            intentFilter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            intentFilter.addAction("android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED")
            intentFilter.addCategory("android.bluetooth.headset.intent.category.companyid.76")
            return intentFilter
        }
    }
}