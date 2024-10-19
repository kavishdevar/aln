package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass

private const val VENDOR_SPECIFIC_HEADSET_EVENT_IPHONEACCEV = "+IPHONEACCEV"
private const val VENDOR_SPECIFIC_HEADSET_EVENT_IPHONEACCEV_BATTERY_LEVEL = 1
private const val APPLE = 0x004C
const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"
private const val PACKAGE_ASI = "com.google.android.settings.intelligence"
private const val ACTION_ASI_UPDATE_BLUETOOTH_DATA = "batterywidget.impl.action.update_bluetooth_data"
//private const val COMPANION_TYPE_NONE = "COMPANION_NONE"
//const val VENDOR_RESULT_CODE_COMMAND_ANDROID = "+ANDROID"


object ServiceManager {
    private var service: AirPodsService? = null
    @Synchronized
    fun getService(): AirPodsService? {
        return service
    }
    @Synchronized
    fun setService(service: AirPodsService?) {
        this.service = service
    }
}

class AirPodsService : Service() {
    inner class LocalBinder : Binder() {
        fun getService(): AirPodsService = this@AirPodsService
    }

    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder()
    }


    var isConnected: Boolean = false
    private var socket: BluetoothSocket? = null

    fun sendPacket(packet: String) {
        val fromHex = packet.split(" ").map { it.toInt(16).toByte() }
        socket?.outputStream?.write(fromHex.toByteArray())
        socket?.outputStream?.flush()
    }

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
        val bytes = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x2E, strength.toByte(), 0x00, 0x00, 0x00)
        socket?.outputStream?.write(bytes)
        socket?.outputStream?.flush()
    }

    val earDetectionNotification = AirPodsNotifications.EarDetection()
    val ancNotification = AirPodsNotifications.ANC()
    val batteryNotification = AirPodsNotifications.BatteryNotification()
    val conversationAwarenessNotification = AirPodsNotifications.ConversationalAwarenessNotification()

    var earDetectionEnabled = true

    fun setCaseChargingSounds(enabled: Boolean) {
        val bytes = byteArrayOf(0x12, 0x3a, 0x00, 0x01, 0x00, 0x08, if (enabled) 0x00 else 0x01)
        socket?.outputStream?.write(bytes)
        socket?.outputStream?.flush()
    }

    fun setEarDetection(enabled: Boolean) {
        earDetectionEnabled = enabled
    }

    fun getBattery(): List<Battery> {
        return batteryNotification.getBattery()
    }

    fun getANC(): Int {
        return ancNotification.status
    }
//
//    private fun buildBatteryText(battery: List<Battery>): String {
//        val left = battery[0]
//        val right = battery[1]
//        val case = battery[2]
//
//        return "Left: ${left.level}% ${left.getStatusName()}, Right: ${right.level}% ${right.getStatusName()}, Case: ${case.level}% ${case.getStatusName()}"
//    }

    private fun createNotification(): Notification {
        val channelId = "battery"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.pro_2_buds)
            .setContentTitle("AirPods Connected")
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        val channel =
            NotificationChannel(channelId, "Battery Notification", NotificationManager.IMPORTANCE_LOW)

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
        return notificationBuilder.build()
    }

    fun disconnectAudio(context: Context, device: BluetoothDevice?) {
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    try {
                        val method = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.A2DP)

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method = proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.HEADSET)
    }

    fun connectAudio(context: Context, device: BluetoothDevice?) {
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    try {
                        val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.A2DP)

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method = proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) { }
        }, BluetoothProfile.HEADSET)
    }

    fun updatePodsStatus(device: BluetoothDevice, batteryList: List<Battery>) {
        var batteryUnified = 0
        var batteryUnifiedArg = 0

        // Handle each Battery object from batteryList
//            batteryList.forEach { battery ->
//                when (battery.getComponentName()) {
//                    "LEFT" -> {
//                        HiddenApiBypass.invoke(BluetoothDevice::class.java, device, "setMetadata", 10, battery.level.toString().toByteArray())
//                        HiddenApiBypass.invoke(BluetoothDevice::class.java, device, "setMetadata", 13, battery.getStatusName()?.uppercase()?.toByteArray())
//                    }
//                    "RIGHT" -> {
//                        HiddenApiBypass.invoke(BluetoothDevice::class.java, device, "setMetadata", 11, battery.level.toString().toByteArray())
//                        HiddenApiBypass.invoke(BluetoothDevice::class.java, device, "setMetadata", 14, battery.getStatusName()?.uppercase()?.toByteArray())
//                    }
//                    "CASE" -> {
//                        HiddenApiBypass.invoke(BluetoothDevice::class.java, device, "setMetadata", 12, battery.level.toString().toByteArray())
//                        HiddenApiBypass.invoke(BluetoothDevice::class.java, device, "setMetadata", 15, battery.getStatusName()?.uppercase()?.toByteArray())
//                    }
//                }
//            }


        // Sending broadcast for battery update
        broadcastVendorSpecificEventIntent(
            VENDOR_SPECIFIC_HEADSET_EVENT_IPHONEACCEV,
            APPLE,
            BluetoothHeadset.AT_CMD_TYPE_SET,
            batteryUnified,
            batteryUnifiedArg,
            device
        )
    }

    @Suppress("SameParameterValue")
    @SuppressLint("MissingPermission")
    private fun broadcastVendorSpecificEventIntent(
        command: String,
        companyId: Int,
        commandType: Int,
        batteryUnified: Int,
        batteryUnifiedArg: Int,
        device: BluetoothDevice
    ) {
        val arguments = arrayOf(
            1, // Number of key(IndicatorType)/value pairs
            VENDOR_SPECIFIC_HEADSET_EVENT_IPHONEACCEV_BATTERY_LEVEL, // IndicatorType: Battery Level
            batteryUnifiedArg // Battery Level
        )

        val intent = Intent(BluetoothHeadset.ACTION_VENDOR_SPECIFIC_HEADSET_EVENT).apply {
            putExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD, command)
            putExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_CMD_TYPE, commandType)
            putExtra(BluetoothHeadset.EXTRA_VENDOR_SPECIFIC_HEADSET_EVENT_ARGS, arguments)
            putExtra(BluetoothDevice.EXTRA_DEVICE, device)
            putExtra(BluetoothDevice.EXTRA_NAME, device.name)
            addCategory(BluetoothHeadset.VENDOR_SPECIFIC_HEADSET_EVENT_COMPANY_ID_CATEGORY + "." + companyId.toString())
        }
        sendBroadcast(intent)

        val batteryIntent = Intent(ACTION_BATTERY_LEVEL_CHANGED).apply {
            putExtra(BluetoothDevice.EXTRA_DEVICE, device)
            putExtra(EXTRA_BATTERY_LEVEL, batteryUnified)
        }
        sendBroadcast(batteryIntent)

        val statusIntent = Intent(ACTION_ASI_UPDATE_BLUETOOTH_DATA).setPackage(PACKAGE_ASI).apply {
            putExtra(ACTION_BATTERY_LEVEL_CHANGED, intent)
        }
        sendBroadcast(statusIntent)
    }


    fun setName(name: String) {
        val nameBytes = name.toByteArray()
        val bytes = byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x1a, 0x00, 0x01,
            nameBytes.size.toByte(), 0x00) + nameBytes
        socket?.outputStream?.write(bytes)
        socket?.outputStream?.flush()
        val hex = bytes.joinToString(" ") { "%02X".format(it) }
        Log.d("AirPodsService", "setName: $name, sent packet: $hex")
    }

    @SuppressLint("MissingPermission", "InlinedApi")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        val notification = createNotification()
        startForeground(1, notification)

        ServiceManager.setService(this)

        if (isConnected) {
            return START_STICKY
        }
        isConnected = true

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
                sendBroadcast(Intent(AirPodsNotifications.AIRPODS_CONNECTED))
                it.outputStream.flush()

                CoroutineScope(Dispatchers.IO).launch {
                    while (socket?.isConnected == true) {
                        socket?.let {
                            val audioManager = this@AirPodsService.getSystemService(AUDIO_SERVICE) as AudioManager
                            MediaController.initialize(audioManager)
                            val buffer = ByteArray(1024)
                            val bytesRead = it.inputStream.read(buffer)
                            var data: ByteArray = byteArrayOf()
                            if (bytesRead > 0) {
                                data = buffer.copyOfRange(0, bytesRead)
                                sendBroadcast(Intent(AirPodsNotifications.AIRPODS_DATA).apply {
                                    putExtra("data", buffer.copyOfRange(0, bytesRead))
                                })
                                val bytes = buffer.copyOfRange(0, bytesRead)
                                val formattedHex = bytes.joinToString(" ") { "%02X".format(it) }
                                Log.d("AirPods Data", "Data received: $formattedHex")
                            }
                            else if (bytesRead == -1) {
                                Log.d("AirPods Service", "Socket closed (bytesRead = -1)")
                                this@AirPodsService.stopForeground(STOP_FOREGROUND_REMOVE)
                                socket?.close()
                                sendBroadcast(Intent(AirPodsNotifications.AIRPODS_DISCONNECTED))
                                return@launch
                            }
                            var inEar = false
                            var inEarData = listOf<Boolean>()
                            if (earDetectionNotification.isEarDetectionData(data)) {
                                earDetectionNotification.setStatus(data)
                                sendBroadcast(Intent(AirPodsNotifications.EAR_DETECTION_DATA).apply {
                                    val list = earDetectionNotification.status
                                    val bytes = ByteArray(2)
                                    bytes[0] = list[0]
                                    bytes[1] = list[1]
                                    putExtra("data", bytes)
                                })
                                Log.d("AirPods Parser", "Ear Detection: ${earDetectionNotification.status[0]} ${earDetectionNotification.status[1]}")
                                var justEnabledA2dp = false
                                val earReceiver = object : BroadcastReceiver() {
                                    override fun onReceive(context: Context, intent: Intent) {
                                        val data = intent.getByteArrayExtra("data")
                                        if (data != null && earDetectionEnabled) {
                                            inEar = if (data.find { it == 0x02.toByte() } != null || data.find { it == 0x03.toByte() } != null) {
                                                data[0] == 0x00.toByte() || data[1] == 0x00.toByte()
                                            } else {
                                                data[0] == 0x00.toByte() && data[1] == 0x00.toByte()
                                            }

                                            val newInEarData = listOf(data[0] == 0x00.toByte(), data[1] == 0x00.toByte())
                                            if (newInEarData.contains(true) && inEarData == listOf(false, false)) {
                                                connectAudio(this@AirPodsService, device)
                                                justEnabledA2dp = true
                                                val bluetoothAdapter = this@AirPodsService.getSystemService(BluetoothManager::class.java).adapter
                                                bluetoothAdapter.getProfileProxy(
                                                    this@AirPodsService, object : BluetoothProfile.ServiceListener {
                                                        override fun onServiceConnected(
                                                            profile: Int,
                                                            proxy: BluetoothProfile
                                                        ) {
                                                            if (profile == BluetoothProfile.A2DP) {
                                                                val connectedDevices =
                                                                    proxy.connectedDevices
                                                                if (connectedDevices.isNotEmpty()) {
                                                                    MediaController.sendPlay()
                                                                }
                                                            }
                                                            bluetoothAdapter.closeProfileProxy(
                                                                profile,
                                                                proxy
                                                            )
                                                        }

                                                        override fun onServiceDisconnected(
                                                            profile: Int
                                                        ) {
                                                        }
                                                    }
                                                    ,BluetoothProfile.A2DP
                                                )

                                            }
                                            else if (newInEarData == listOf(false, false)){
                                                disconnectAudio(this@AirPodsService, device)
                                            }

                                            inEarData = newInEarData

                                            if (inEar == true) {
                                                if (!justEnabledA2dp) {
                                                    justEnabledA2dp = false
                                                    MediaController.sendPlay()
                                                }
                                            } else {
                                                MediaController.sendPause()
                                            }
                                        }
                                    }
                                }

                                val earIntentFilter = IntentFilter(AirPodsNotifications.EAR_DETECTION_DATA)
                                this@AirPodsService.registerReceiver(earReceiver, earIntentFilter,
                                    RECEIVER_EXPORTED
                                )
                            }
                            else if (ancNotification.isANCData(data)) {
                                ancNotification.setStatus(data)
                                sendBroadcast(Intent(AirPodsNotifications.ANC_DATA).apply {
                                    putExtra("data", ancNotification.status)
                                })
                                Log.d("AirPods Parser", "ANC: ${ancNotification.status}")
                            }
                            else if (batteryNotification.isBatteryData(data)) {
                                batteryNotification.setBattery(data)
                                sendBroadcast(Intent(AirPodsNotifications.BATTERY_DATA).apply {
                                    putParcelableArrayListExtra("data", ArrayList(batteryNotification.getBattery()))
                                })
                                for (battery in batteryNotification.getBattery()) {
                                    Log.d("AirPods Parser", "${battery.getComponentName()}: ${battery.getStatusName()} at ${battery.level}% ")
                                }
//                                updatePodsStatus(device!!, batteryNotification.getBattery())
                            }
                            else if (conversationAwarenessNotification.isConversationalAwarenessData(data)) {
                                conversationAwarenessNotification.setData(data)
                                sendBroadcast(Intent(AirPodsNotifications.CA_DATA).apply {
                                    putExtra("data", conversationAwarenessNotification.status)
                                })


                                if (conversationAwarenessNotification.status == 1.toByte() || conversationAwarenessNotification.status == 2.toByte()) {
                                    MediaController.startSpeaking()
                                } else if (conversationAwarenessNotification.status == 8.toByte() || conversationAwarenessNotification.status == 9.toByte()) {
                                    MediaController.stopSpeaking()
                                }

                                Log.d("AirPods Parser", "Conversation Awareness: ${conversationAwarenessNotification.status}")
                            }
                            else { }
                        }
                    }
                    Log.d("AirPods Service", "Socket closed")
                    isConnected = false
                    this@AirPodsService.stopForeground(STOP_FOREGROUND_REMOVE)
                    socket?.close()
                    sendBroadcast(Intent(AirPodsNotifications.AIRPODS_DISCONNECTED))
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
        isConnected = false
        ServiceManager.setService(null)
    }
}