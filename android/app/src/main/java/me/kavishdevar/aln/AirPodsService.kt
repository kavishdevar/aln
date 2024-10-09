package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

class AirPodsService : Service() {
    inner class LocalBinder : Binder() {
        fun getService(): AirPodsService = this@AirPodsService
    }

    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder()
    }

    var isRunning: Boolean = false
    private var socket: BluetoothSocket? = null

    fun setANCMode(mode: Int) {
        when (mode) {
            1 -> {
                socket?.outputStream?.write(Enums.NOISE_CANCELLATION_OFF.value)
            }
            2 -> {
                socket?.outputStream?.write(Enums.NOISE_CANCELLATION_ON.value)
            }
            3 -> {
                socket?.outputStream?.write(Enums.NOISE_CANCELLATION_TRANSPARENCY.value)
            }
            4 -> {
                socket?.outputStream?.write(Enums.NOISE_CANCELLATION_ADAPTIVE.value)
            }
        }
        socket?.outputStream?.flush()
    }

    fun setCAEnabled(enabled: Boolean) {
        socket?.outputStream?.write(if (enabled) Enums.SET_CONVERSATION_AWARENESS_ON.value else Enums.SET_CONVERSATION_AWARENESS_OFF.value)
    }

    fun setAdaptiveStrength(strength: Int) {
        val bytes = byteArrayOf(0x00, 0x04, 0x00, 0x09, 0x00, 0x2E, strength.toByte(), 0x00, 0x00, 0x00)
        val hexString = bytes.joinToString(" ") { "%02X".format(it) }
        Log.d("AirPodsService", "Adaptive Strength: $hexString")
        socket?.outputStream?.write(bytes)
        socket?.outputStream?.flush()
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) {
            return START_STICKY
        }
        isRunning = true

        @Suppress("DEPRECATION") val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) intent?.getParcelableExtra("device", BluetoothDevice::class.java) else intent?.getParcelableExtra("device")

        HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/BluetoothSocket;")
        val uuid: ParcelUuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")
        socket = HiddenApiBypass.newInstance(BluetoothSocket::class.java, 3, true, true, device, 0x1001, uuid) as BluetoothSocket?
        try {
            socket?.connect()
            socket?.let { it ->
                it.outputStream.write(Enums.HANDSHAKE.value)
                it.outputStream.write(Enums.SET_SPECIFIC_FEATURES.value)
                it.outputStream.write(Enums.REQUEST_NOTIFICATIONS.value)
                sendBroadcast(Intent(Notifications.AIRPODS_CONNECTED))
                it.outputStream.flush()

                CoroutineScope(Dispatchers.IO).launch {
                    val earDetectionNotification = Notifications.EarDetection()
                    val ancNotification = Notifications.ANC()
                    val batteryNotification = Notifications.BatteryNotification()
                    val conversationAwarenessNotification = Notifications.ConversationalAwarenessNotification()

                    while (socket?.isConnected == true) {
                        socket?.let {
                            val buffer = ByteArray(1024)
                            val bytesRead = it.inputStream.read(buffer)
                            val data = buffer.copyOfRange(0, bytesRead)
                            if (bytesRead > 0) {
                                sendBroadcast(Intent(Notifications.AIRPODS_DATA).apply {
                                    putExtra("data", buffer.copyOfRange(0, bytesRead))
                                })
                                val bytes = buffer.copyOfRange(0, bytesRead)
                                val formattedHex = bytes.joinToString(" ") { "%02X".format(it) }
                                Log.d("AirPods Data", "Data received: $formattedHex")
                            }
                            if (earDetectionNotification.isEarDetectionData(data)) {
                                earDetectionNotification.setStatus(data)
                                sendBroadcast(Intent(Notifications.EAR_DETECTION_DATA).apply {
                                    val list = earDetectionNotification.status
                                    val bytes = ByteArray(2)
                                    bytes[0] = list[0]
                                    bytes[1] = list[1]
                                    putExtra("data", bytes)
                                })
                                Log.d("AirPods Parser", "Ear Detection: ${earDetectionNotification.status[0]} ${earDetectionNotification.status[1]}")
                            }
                            else if (ancNotification.isANCData(data)) {
                                ancNotification.setStatus(data)
                                sendBroadcast(Intent(Notifications.ANC_DATA).apply {
                                    putExtra("data", ancNotification.status)
                                })
                                Log.d("AirPods Parser", "ANC: ${ancNotification.status}")
                            }
                            else if (batteryNotification.isBatteryData(data)) {
                                batteryNotification.setBattery(data)
                                sendBroadcast(Intent(Notifications.BATTERY_DATA).apply {
                                    putParcelableArrayListExtra("data", ArrayList(batteryNotification.getBattery()))
                                })
                                for (battery in batteryNotification.getBattery()) {
                                    Log.d("AirPods Parser", "${battery.getComponentName()}: ${battery.getStatusName()} at ${battery.level}% ")
                                }
                            }
                            else if (conversationAwarenessNotification.isConversationalAwarenessData(data)) {
                                conversationAwarenessNotification.setData(data)
                                sendBroadcast(Intent(Notifications.CA_DATA).apply {
                                    putExtra("data", conversationAwarenessNotification.status)
                                })
                                Log.d("AirPods Parser", "Conversation Awareness: ${conversationAwarenessNotification.status}")
                            }
                            else { }
                        }
                    }
                    Log.d("AirPods Service", "Socket closed")
                    isRunning = false
                }
            }
        }
        catch (e: Exception) {
            Log.e("AirPodsSettingsScreen", "Error connecting to device: ${e.message}")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        socket?.close()
        isRunning = false
    }
}