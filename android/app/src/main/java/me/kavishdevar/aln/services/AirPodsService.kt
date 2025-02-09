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

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Resources
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelUuid
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresPermission
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import me.kavishdevar.aln.MainActivity
import me.kavishdevar.aln.R
import me.kavishdevar.aln.utils.AirPodsNotifications
import me.kavishdevar.aln.utils.Battery
import me.kavishdevar.aln.utils.BatteryComponent
import me.kavishdevar.aln.utils.BatteryStatus
import me.kavishdevar.aln.utils.CrossDevice
import me.kavishdevar.aln.utils.CrossDevicePackets
import me.kavishdevar.aln.utils.Enums
import me.kavishdevar.aln.utils.IslandType
import me.kavishdevar.aln.utils.IslandWindow
import me.kavishdevar.aln.utils.LongPressPackets
import me.kavishdevar.aln.utils.MediaController
import me.kavishdevar.aln.utils.PopupWindow
import me.kavishdevar.aln.widgets.BatteryWidget
import me.kavishdevar.aln.widgets.NoiseControlWidget
import org.lsposed.hiddenapibypass.HiddenApiBypass

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

    @OptIn(ExperimentalMaterial3Api::class)
    @Synchronized
    fun restartService(context: Context) {
        service?.stopSelf()
        Log.d("ServiceManager", "Restarting service, service is null: ${service == null}")
        val intent = Intent(context, AirPodsService::class.java)
        context.stopService(intent)
        CoroutineScope(Dispatchers.IO).launch {
            delay(1000)
            context.startService(intent)
            context.startActivity(Intent(context, MainActivity::class.java))
            service?.clearLogs()
        }
    }
}

// @Suppress("unused")
class AirPodsService : Service() {
    var macAddress = ""

    inner class LocalBinder : Binder() {
        fun getService(): AirPodsService = this@AirPodsService
    }

    private lateinit var sharedPreferencesLogs: SharedPreferences
    private lateinit var sharedPreferences: SharedPreferences
    private val packetLogKey = "packet_log"
    private val _packetLogsFlow = MutableStateFlow<Set<String>>(emptySet())
    val packetLogsFlow: StateFlow<Set<String>> get() = _packetLogsFlow

    private lateinit var telephonyManager: TelephonyManager
    private lateinit var phoneStateListener: PhoneStateListener

    override fun onCreate() {
        super.onCreate()
        sharedPreferencesLogs = getSharedPreferences("packet_logs", MODE_PRIVATE)
    }

    private fun logPacket(packet: ByteArray, source: String) {
        val packetHex = packet.joinToString(" ") { "%02X".format(it) }
        val logEntry = "$source: $packetHex"
        val logs =
            sharedPreferencesLogs.getStringSet(packetLogKey, mutableSetOf())?.toMutableSet()
                ?: mutableSetOf()
        logs.add(logEntry)
        _packetLogsFlow.value = logs
        sharedPreferencesLogs.edit { putStringSet(packetLogKey, logs) }
    }

    fun getPacketLogs(): Set<String> {
        return sharedPreferencesLogs.getStringSet(packetLogKey, emptySet()) ?: emptySet()
    }

    private fun clearPacketLogs() {
        sharedPreferencesLogs.edit { remove(packetLogKey).apply() }

    }

    fun clearLogs() {
        clearPacketLogs() // Expose a method to clear logs
        _packetLogsFlow.value = emptySet()
    }

    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder()
    }

    var popupShown = false

    fun showPopup(service: Service, name: String) {
        if (popupShown) {
            return
        }
        val popupWindow = PopupWindow(service.applicationContext)
        popupWindow.open(name, batteryNotification)
        popupShown = true
    }
    var islandOpen = false
    var islandWindow: IslandWindow? = null
    @SuppressLint("MissingPermission")
    fun showIsland(service: Service, batteryPercentage: Int, type: IslandType = IslandType.CONNECTED) {
        Log.d("AirPodsService", "Showing island window")
        CoroutineScope(Dispatchers.Main).launch {
            islandWindow = IslandWindow(service.applicationContext)
            islandWindow!!.show(sharedPreferences.getString("name", "AirPods Pro").toString(), batteryPercentage, this@AirPodsService, type)
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun startMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    @Suppress("ClassName")
    private object bluetoothReceiver : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent) {
            val bluetoothDevice =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(
                        "android.bluetooth.device.extra.DEVICE",
                        BluetoothDevice::class.java
                    )
                } else {
                    intent.getParcelableExtra("android.bluetooth.device.extra.DEVICE") as BluetoothDevice?
                }
            val action = intent.action
            val context = context?.applicationContext
            val name = context?.getSharedPreferences("settings", MODE_PRIVATE)
                ?.getString("name", bluetoothDevice?.name)
            if (bluetoothDevice != null && action != null && !action.isEmpty()) {
                Log.d("AirPodsService", "Received bluetooth connection broadcast")
                if (BluetoothDevice.ACTION_ACL_CONNECTED == action) {
                    if (ServiceManager.getService()?.isConnectedLocally == true) {
                        ServiceManager.getService()?.manuallyCheckForAudioSource()
                        return
                    }
                    val uuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")
                    bluetoothDevice.fetchUuidsWithSdp()
                    if (bluetoothDevice.uuids != null) {
                        if (bluetoothDevice.uuids.contains(uuid)) {
                            val intent =
                                Intent(AirPodsNotifications.Companion.AIRPODS_CONNECTION_DETECTED)
                            intent.putExtra("name", name)
                            intent.putExtra("device", bluetoothDevice)
                            context?.sendBroadcast(intent)
                        }
                    }
                }
            }
        }
    }

    var isConnectedLocally = false
    var device: BluetoothDevice? = null

    private lateinit var earReceiver: BroadcastReceiver
    var widgetMobileBatteryEnabled = false

    object BatteryChangedIntentReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                ServiceManager.getService()?.updateBatteryWidget()
            } else if (intent.action == AirPodsNotifications.DISCONNECT_RECEIVERS) {
                try {
                    context?.unregisterReceiver(this)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun setPhoneBatteryInWidget(enabled: Boolean) {
        widgetMobileBatteryEnabled = enabled
        updateBatteryWidget()
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun startForegroundNotification() {
        val notificationChannel = NotificationChannel(
            "background_service_status",
            "Background Service Status",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(notificationChannel)

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "background_service_status")
            .setSmallIcon(R.drawable.airpods)
            .setContentTitle("AirPods not connected")
            .setContentText("Tap to open app")
            .setContentIntent(pendingIntent)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        try {
            startForeground(1, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun sendANCBroadcast() {
        sendBroadcast(Intent(AirPodsNotifications.Companion.ANC_DATA).apply {
            putExtra("data", ancNotification.status)
        })
    }

    fun sendBatteryBroadcast() {
        sendBroadcast(Intent(AirPodsNotifications.Companion.BATTERY_DATA).apply {
            putParcelableArrayListExtra("data", ArrayList(batteryNotification.getBattery()))
        })
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendBatteryNotification() {
        updateNotificationContent(
            true,
            getSharedPreferences("settings", MODE_PRIVATE).getString("name", device?.name),
            batteryNotification.getBattery()
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun updateBatteryWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, BatteryWidget::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)

        val remoteViews = RemoteViews(packageName, R.layout.battery_widget).also {
            val openActivityIntent = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            it.setOnClickPendingIntent(R.id.battery_widget, openActivityIntent)

            val leftBattery =
                batteryNotification.getBattery().find { it.component == BatteryComponent.LEFT }
            val rightBattery =
                batteryNotification.getBattery().find { it.component == BatteryComponent.RIGHT }
            val caseBattery =
                batteryNotification.getBattery().find { it.component == BatteryComponent.CASE }

            it.setTextViewText(
                R.id.left_battery_widget,
                leftBattery?.let {
                    "${it.level}%"
                } ?: ""
            )
            it.setProgressBar(
                R.id.left_battery_progress,
                100,
                leftBattery?.level ?: 0,
                false
            )
            it.setViewVisibility(
                R.id.left_charging_icon,
                if (leftBattery?.status == BatteryStatus.CHARGING) View.VISIBLE else View.GONE
            )

            it.setTextViewText(
                R.id.right_battery_widget,
                rightBattery?.let {
                    "${it.level}%"
                } ?: ""
            )
            it.setProgressBar(
                R.id.right_battery_progress,
                100,
                rightBattery?.level ?: 0,
                false
            )
            it.setViewVisibility(
                R.id.right_charging_icon,
                if (rightBattery?.status == BatteryStatus.CHARGING) View.VISIBLE else View.GONE
            )

            it.setTextViewText(
                R.id.case_battery_widget,
                caseBattery?.let {
                    "${it.level}%"
                } ?: ""
            )
            it.setProgressBar(
                R.id.case_battery_progress,
                100,
                caseBattery?.level ?: 0,
                false
            )
            it.setViewVisibility(
                R.id.case_charging_icon,
                if (caseBattery?.status == BatteryStatus.CHARGING) View.VISIBLE else View.GONE
            )

            it.setViewVisibility(
                R.id.phone_battery_widget_container,
                if (widgetMobileBatteryEnabled) View.VISIBLE else View.GONE
            )
            if (widgetMobileBatteryEnabled) {
                val batteryManager = getSystemService<BatteryManager>(BatteryManager::class.java)
                val batteryLevel =
                    batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                val charging =
                    batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) == BatteryManager.BATTERY_STATUS_CHARGING
                it.setTextViewText(
                    R.id.phone_battery_widget,
                    "$batteryLevel%"
                )
                it.setViewVisibility(
                    R.id.phone_charging_icon,
                    if (charging) View.VISIBLE else View.GONE
                )
                it.setProgressBar(
                    R.id.phone_battery_progress,
                    100,
                    batteryLevel,
                    false
                )
            }
        }
        appWidgetManager.updateAppWidget(widgetIds, remoteViews)
    }

    fun updateNoiseControlWidget() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, NoiseControlWidget::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        val remoteViews = RemoteViews(packageName, R.layout.noise_control_widget).also {
            val ancStatus = ancNotification.status
            it.setInt(
                R.id.widget_off_button,
                "setBackgroundResource",
                if (ancStatus == 1) R.drawable.widget_button_checked_shape_start else R.drawable.widget_button_shape_start
            )
            it.setInt(
                R.id.widget_transparency_button,
                "setBackgroundResource",
                if (ancStatus == 3) (if (sharedPreferences.getBoolean("off_listening_mode", true)) R.drawable.widget_button_checked_shape_middle else R.drawable.widget_button_checked_shape_start) else (if (sharedPreferences.getBoolean("off_listening_mode", true)) R.drawable.widget_button_shape_middle else R.drawable.widget_button_shape_start)
            )
            it.setInt(
                R.id.widget_adaptive_button,
                "setBackgroundResource",
                if (ancStatus == 4) R.drawable.widget_button_checked_shape_middle else R.drawable.widget_button_shape_middle
            )
            it.setInt(
                R.id.widget_anc_button,
                "setBackgroundResource",
                if (ancStatus == 2) R.drawable.widget_button_checked_shape_end else R.drawable.widget_button_shape_end
            )
            it.setViewVisibility(
                R.id.widget_off_button,
                if (sharedPreferences.getBoolean("off_listening_mode", true)) View.VISIBLE else View.GONE
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                it.setViewLayoutMargin(
                    R.id.widget_transparency_button,
                    RemoteViews.MARGIN_START,
                    if (sharedPreferences.getBoolean("off_listening_mode", true)) 2f else 12f,
                    TypedValue.COMPLEX_UNIT_DIP
                )
            } else {
                it.setViewPadding(
                    R.id.widget_transparency_button,
                    if (sharedPreferences.getBoolean("off_listening_mode", true)) 2.dpToPx() else 12.dpToPx(),
                    12.dpToPx(),
                    2.dpToPx(),
                    12.dpToPx()
                )
            }
        }

        appWidgetManager.updateAppWidget(widgetIds, remoteViews)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    fun updateNotificationContent(
        connected: Boolean,
        airpodsName: String? = null,
        batteryList: List<Battery>? = null
    ) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        var updatedNotification: Notification? = null

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (connected) {
            updatedNotification = NotificationCompat.Builder(this, "background_service_status")
                .setSmallIcon(R.drawable.airpods)
                .setContentTitle(airpodsName)
                .setContentText(
                    """${
                        batteryList?.find { it.component == BatteryComponent.LEFT }?.let {
                            if (it.status != BatteryStatus.DISCONNECTED) {
                                "L: ${if (it.status == BatteryStatus.CHARGING) "⚡" else ""} ${it.level}%"
                            } else {
                                ""
                            }
                        } ?: ""
                    } ${
                        batteryList?.find { it.component == BatteryComponent.RIGHT }?.let {
                            if (it.status != BatteryStatus.DISCONNECTED) {
                                "R: ${if (it.status == BatteryStatus.CHARGING) "⚡" else ""} ${it.level}%"
                            } else {
                                ""
                            }
                        } ?: ""
                    } ${
                        batteryList?.find { it.component == BatteryComponent.CASE }?.let {
                            if (it.status != BatteryStatus.DISCONNECTED) {
                                "Case: ${if (it.status == BatteryStatus.CHARGING) "⚡" else ""} ${it.level}%"
                            } else {
                                ""
                            }
                        } ?: ""
                    }""")
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
        } else {
            updatedNotification = NotificationCompat.Builder(this, "background_service_status")
                .setSmallIcon(R.drawable.airpods)
                .setContentTitle("AirPods not connected")
                .setContentText("Tap to open app")
                .setContentIntent(pendingIntent)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build()
        }

        notificationManager.notify(1, updatedNotification)
    }

    private lateinit var connectionReceiver: BroadcastReceiver
    private lateinit var disconnectionReceiver: BroadcastReceiver

    @SuppressLint("InlinedApi", "MissingPermission", "UnspecifiedRegisterReceiverFlag")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("AirPodsService", "Service started")
        ServiceManager.setService(this)
        startForegroundNotification()
        val audioManager =
            this@AirPodsService.getSystemService(AUDIO_SERVICE) as AudioManager
        MediaController.initialize(
            audioManager,
            this@AirPodsService.getSharedPreferences(
                "settings",
                MODE_PRIVATE
            )
        )
        Log.d("AirPodsService", "Initializing CrossDevice")
        CoroutineScope(Dispatchers.IO).launch {
            CrossDevice.init(this@AirPodsService)
            Log.d("AirPodsService", "CrossDevice initialized")
        }

        sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
        macAddress = sharedPreferences.getString("mac_address", "") ?: ""

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        phoneStateListener = object : PhoneStateListener() {
            @SuppressLint("SwitchIntDef")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                when (state) {
                    TelephonyManager.CALL_STATE_RINGING -> {
                        if (CrossDevice.isAvailable && !isConnectedLocally && earDetectionNotification.status.contains(0x00)) takeOver()
                    }
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        if (CrossDevice.isAvailable && !isConnectedLocally && earDetectionNotification.status.contains(0x00)) takeOver()
                    }
                }
            }
        }
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)

        if (sharedPreferences.getBoolean("show_phone_battery_in_widget", true)) {
            widgetMobileBatteryEnabled = true
            val batteryChangedIntentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            batteryChangedIntentFilter.addAction(AirPodsNotifications.DISCONNECT_RECEIVERS)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(
                    BatteryChangedIntentReceiver,
                    batteryChangedIntentFilter,
                    RECEIVER_EXPORTED
                )
            } else {
                registerReceiver(BatteryChangedIntentReceiver, batteryChangedIntentFilter)
            }
        }
        val serviceIntentFilter = IntentFilter().apply {
            addAction("android.bluetooth.device.action.ACL_CONNECTED")
            addAction("android.bluetooth.device.action.ACL_DISCONNECTED")
            addAction("android.bluetooth.device.action.BOND_STATE_CHANGED")
            addAction("android.bluetooth.device.action.NAME_CHANGED")
            addAction("android.bluetooth.adapter.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.adapter.action.STATE_CHANGED")
            addAction("android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.headset.action.VENDOR_SPECIFIC_HEADSET_EVENT")
            addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.a2dp.profile.action.PLAYING_STATE_CHANGED")
        }

        connectionReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AirPodsNotifications.Companion.AIRPODS_CONNECTION_DETECTED) {
                    device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra("device", BluetoothDevice::class.java)!!
                    } else {
                        intent.getParcelableExtra("device") as BluetoothDevice?
                    }
                    val name = this@AirPodsService.getSharedPreferences("settings", MODE_PRIVATE)
                        .getString("name", device?.name)
                    if (this@AirPodsService.getSharedPreferences("settings", MODE_PRIVATE)
                            .getString("name", null) == null
                    ) {
                        this@AirPodsService.getSharedPreferences("settings", MODE_PRIVATE).edit {
                            putString("name", name)
                        }
                    }
                    Log.d("AirPodsCrossDevice", CrossDevice.isAvailable.toString())
                    if (!CrossDevice.isAvailable) {
                        Log.d("AirPodsService", "$name connected")
                        showPopup(this@AirPodsService, name.toString())
                        connectToSocket(device!!)
                        isConnectedLocally = true
                        macAddress = device!!.address
                        sharedPreferences.edit {
                            putString("mac_address", macAddress)
                        }
                        updateNotificationContent(
                            true,
                            name.toString(),
                            batteryNotification.getBattery()
                        )
                    }
                } else if (intent?.action == AirPodsNotifications.Companion.AIRPODS_DISCONNECTED) {
                    device = null
                    isConnectedLocally = false
                    popupShown = false
                    updateNotificationContent(false)
                }
            }
        }
         val showIslandReceiver = object: BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "me.kavishdevar.aln.cross_device_island") {
                    showIsland(this@AirPodsService, batteryNotification.getBattery().find { it.component == BatteryComponent.LEFT}?.level!!.coerceAtMost(batteryNotification.getBattery().find { it.component == BatteryComponent.RIGHT}?.level!!))
                } else if (intent?.action == AirPodsNotifications.Companion.DISCONNECT_RECEIVERS) {
                    try {
                        context?.unregisterReceiver(this)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        val showIslandIntentFilter = IntentFilter().apply {
            addAction("me.kavishdevar.aln.cross_device_island")
            addAction(AirPodsNotifications.DISCONNECT_RECEIVERS)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(showIslandReceiver, showIslandIntentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(showIslandReceiver, showIslandIntentFilter)
        }

        val deviceIntentFilter = IntentFilter().apply {
            addAction(AirPodsNotifications.Companion.AIRPODS_CONNECTION_DETECTED)
            addAction(AirPodsNotifications.Companion.AIRPODS_DISCONNECTED)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(connectionReceiver, deviceIntentFilter, RECEIVER_EXPORTED)
            registerReceiver(bluetoothReceiver, serviceIntentFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(connectionReceiver, deviceIntentFilter)
            registerReceiver(bluetoothReceiver, serviceIntentFilter)
        }

        val bluetoothAdapter = getSystemService(BluetoothManager::class.java).adapter

        bluetoothAdapter.bondedDevices.forEach { device ->
            device.fetchUuidsWithSdp()
            if (device.uuids != null) {
                if (device.uuids.contains(ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a"))) {
                    bluetoothAdapter.getProfileProxy(
                        this,
                        object : BluetoothProfile.ServiceListener {
                            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                                if (profile == BluetoothProfile.A2DP) {
                                    val connectedDevices = proxy.connectedDevices
                                    if (connectedDevices.isNotEmpty()) {
                                        if (!CrossDevice.isAvailable) {
                                            connectToSocket(device)
                                            macAddress = device.address
                                            sharedPreferences.edit {
                                                putString("mac_address", macAddress)
                                            }
                                        }
                                        this@AirPodsService.sendBroadcast(
                                            Intent(AirPodsNotifications.Companion.AIRPODS_CONNECTED)
                                        )
                                    }
                                }
                                bluetoothAdapter.closeProfileProxy(profile, proxy)
                            }

                            override fun onServiceDisconnected(profile: Int) {}
                        },
                        BluetoothProfile.A2DP
                    )
                }
            }
        }

        if (!isConnectedLocally && !CrossDevice.isAvailable) {
            clearPacketLogs() // Clear logs when device is not available
        }

        return START_STICKY
    }

    private lateinit var socket: BluetoothSocket

    fun manuallyCheckForAudioSource() {
        if (earDetectionNotification.status[0] != 0.toByte() && earDetectionNotification.status[1] != 0.toByte()) {
            Log.d(
                "AirPodsService",
                "For some reason, Android connected to the audio profile itself even after disconnecting. Disconnecting audio profile again!"
            )
            disconnectAudio(this, device)
        }
    }

    @SuppressLint("MissingPermission")
    fun takeOver() {
        Log.d("AirPodsService", "Taking over audio")
        CrossDevice.sendRemotePacket(CrossDevicePackets.REQUEST_DISCONNECT.packet)
        Log.d("AirPodsService", macAddress)
        CrossDevice.isAvailable = false
        sharedPreferences.edit { putBoolean("CrossDeviceIsAvailable", false) }
        device = getSystemService<BluetoothManager>(BluetoothManager::class.java).adapter.bondedDevices.find {
            it.address == macAddress
        }
        if (device != null) {
            connectToSocket(device!!)
            connectAudio(this, device)
        }
        showIsland(this, batteryNotification.getBattery().find { it.component == BatteryComponent.LEFT}?.level!!.coerceAtMost(batteryNotification.getBattery().find { it.component == BatteryComponent.RIGHT}?.level!!),
            IslandType.TAKING_OVER)

        isConnectedLocally = true
        CrossDevice.isAvailable = false
    }

    @SuppressLint("MissingPermission", "UnspecifiedRegisterReceiverFlag")
    fun connectToSocket(device: BluetoothDevice) {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/BluetoothSocket;")
        val uuid: ParcelUuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")

        if (isConnectedLocally != true && !CrossDevice.isAvailable) {
            try {
                socket = HiddenApiBypass.newInstance(
                    BluetoothSocket::class.java,
                    3,
                    true,
                    true,
                    device,
                    0x1001,
                    uuid
                ) as BluetoothSocket
            } catch (e: Exception) {
                e.printStackTrace()
                try {
                    socket = HiddenApiBypass.newInstance(
                        BluetoothSocket::class.java,
                        3,
                        1,
                        true,
                        true,
                        device,
                        0x1001,
                        uuid
                    ) as BluetoothSocket
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            try {
                socket.connect()
                this@AirPodsService.device = device
                isConnectedLocally = true
                socket.let { it ->
                    it.outputStream.write(Enums.HANDSHAKE.value)
                    it.outputStream.flush()
                    it.outputStream.write(Enums.SET_SPECIFIC_FEATURES.value)
                    it.outputStream.flush()
                    it.outputStream.write(Enums.REQUEST_NOTIFICATIONS.value)
                    it.outputStream.flush()
                    CoroutineScope(Dispatchers.IO).launch {
                        it.outputStream.write(Enums.HANDSHAKE.value)
                        it.outputStream.flush()
                        delay(200)
                        it.outputStream.write(Enums.SET_SPECIFIC_FEATURES.value)
                        it.outputStream.flush()
                        delay(200)
                        it.outputStream.write(Enums.REQUEST_NOTIFICATIONS.value)
                        it.outputStream.flush()
                        delay(200)
                        Handler(Looper.getMainLooper()).postDelayed({
                            it.outputStream.write(Enums.HANDSHAKE.value)
                            it.outputStream.flush()
                            it.outputStream.write(Enums.SET_SPECIFIC_FEATURES.value)
                            it.outputStream.flush()
                            it.outputStream.write(Enums.REQUEST_NOTIFICATIONS.value)
                            it.outputStream.flush()
                        }, 5000)
                        sendBroadcast(
                            Intent(AirPodsNotifications.Companion.AIRPODS_CONNECTED)
                                .putExtra("device", device)
                        )
                        while (socket.isConnected == true) {
                            socket.let {
                                val buffer = ByteArray(1024)
                                val bytesRead = it.inputStream.read(buffer)
                                var data: ByteArray = byteArrayOf()
                                if (bytesRead > 0) {
                                    data = buffer.copyOfRange(0, bytesRead)
                                    logPacket(data, "AirPods")
                                    sendBroadcast(Intent(AirPodsNotifications.Companion.AIRPODS_DATA).apply {
                                        putExtra("data", buffer.copyOfRange(0, bytesRead))
                                    })
                                    val bytes = buffer.copyOfRange(0, bytesRead)
                                    val formattedHex = bytes.joinToString(" ") { "%02X".format(it) }
                                    CrossDevice.sendReceivedPacket(bytes)
                                    updateNotificationContent(
                                        true,
                                        sharedPreferences.getString("name", device.name),
                                        batteryNotification.getBattery()
                                    )
                                    Log.d("AirPods Data", "Data received: $formattedHex")
                                } else if (bytesRead == -1) {
                                    Log.d("AirPods Service", "Socket closed (bytesRead = -1)")
                                    sendBroadcast(Intent(AirPodsNotifications.Companion.AIRPODS_DISCONNECTED))
                                    return@launch
                                }
                                var inEar = false
                                var inEarData = listOf<Boolean>()
                                if (earDetectionNotification.isEarDetectionData(data)) {
                                    earDetectionNotification.setStatus(data)
                                    sendBroadcast(Intent(AirPodsNotifications.Companion.EAR_DETECTION_DATA).apply {
                                        val list = earDetectionNotification.status
                                        val bytes = ByteArray(2)
                                        bytes[0] = list[0]
                                        bytes[1] = list[1]
                                        putExtra("data", bytes)
                                    })
                                    Log.d(
                                        "AirPods Parser",
                                        "Ear Detection: ${earDetectionNotification.status[0]} ${earDetectionNotification.status[1]}"
                                    )
                                    var justEnabledA2dp = false
                                    earReceiver = object : BroadcastReceiver() {
                                        override fun onReceive(context: Context, intent: Intent) {
                                            val data = intent.getByteArrayExtra("data")
                                            if (data != null && earDetectionEnabled) {
                                                inEar =
                                                    if (data.find { it == 0x02.toByte() } != null || data.find { it == 0x03.toByte() } != null) {
                                                        data[0] == 0x00.toByte() || data[1] == 0x00.toByte()
                                                    } else {
                                                        data[0] == 0x00.toByte() && data[1] == 0x00.toByte()
                                                    }
                                                val newInEarData = listOf(
                                                    data[0] == 0x00.toByte(),
                                                    data[1] == 0x00.toByte()
                                                )
                                                if (inEarData.sorted() == listOf(false, false) && newInEarData.sorted() != listOf(false, false) && islandWindow?.isVisible != true) {
                                                    showIsland(this@AirPodsService, batteryNotification.getBattery().find { it.component == BatteryComponent.LEFT}?.level!!.coerceAtMost(batteryNotification.getBattery().find { it.component == BatteryComponent.RIGHT}?.level!!))
                                                }
                                                if (newInEarData == listOf(false, false) && islandWindow?.isVisible == true) {
                                                    islandWindow?.close()
                                                }
                                                if (newInEarData.contains(true) && inEarData == listOf(
                                                        false,
                                                        false
                                                    )
                                                ) {
                                                    connectAudio(this@AirPodsService, device)
                                                    justEnabledA2dp = true
                                                    val bluetoothAdapter =
                                                        this@AirPodsService.getSystemService(
                                                            BluetoothManager::class.java
                                                        ).adapter
                                                    bluetoothAdapter.getProfileProxy(
                                                        this@AirPodsService,
                                                        object : BluetoothProfile.ServiceListener {
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
                                                        },
                                                        BluetoothProfile.A2DP
                                                    )

                                                } else if (newInEarData == listOf(false, false)) {
                                                    disconnectAudio(this@AirPodsService, device)
                                                }

                                                if (inEarData.contains(false) && newInEarData == listOf(
                                                        true,
                                                        true
                                                    )
                                                ) {
                                                    Log.d(
                                                        "AirPods Parser",
                                                        "User put in both AirPods from just one."
                                                    )
                                                    MediaController.userPlayedTheMedia = false
                                                }
                                                if (newInEarData.contains(false) && inEarData == listOf(
                                                        true,
                                                        true
                                                    )
                                                ) {
                                                    Log.d(
                                                        "AirPods Parser",
                                                        "User took one of two out."
                                                    )
                                                    MediaController.userPlayedTheMedia = false
                                                }

                                                Log.d(
                                                    "AirPods Parser",
                                                    "inEarData: ${inEarData.sorted()}, newInEarData: ${newInEarData.sorted()}"
                                                )
                                                if (newInEarData.sorted() == inEarData.sorted()) {
                                                    Log.d("AirPods Parser", "hi")
                                                    return
                                                }
                                                Log.d(
                                                    "AirPods Parser",
                                                    "this shouldn't be run if the last log was 'hi'."
                                                )

                                                inEarData = newInEarData

                                                if (inEar == true) {
                                                    if (!justEnabledA2dp) {
                                                        justEnabledA2dp = false
                                                        MediaController.sendPlay()
                                                        MediaController.iPausedTheMedia = false
                                                    }
                                                } else {
                                                    MediaController.sendPause()
                                                }
                                            }
                                        }
                                    }

                                    val earIntentFilter =
                                        IntentFilter(AirPodsNotifications.Companion.EAR_DETECTION_DATA)
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        this@AirPodsService.registerReceiver(
                                            earReceiver, earIntentFilter,
                                            RECEIVER_EXPORTED
                                        )
                                    } else {
                                        this@AirPodsService.registerReceiver(
                                            earReceiver,
                                            earIntentFilter
                                        )
                                    }
                                } else if (ancNotification.isANCData(data)) {
                                    CrossDevice.sendRemotePacket(data)
                                    CrossDevice.ancBytes = data
                                    ancNotification.setStatus(data)
                                    sendANCBroadcast()
                                    updateNoiseControlWidget()
                                    Log.d("AirPods Parser", "ANC: ${ancNotification.status}")
                                } else if (batteryNotification.isBatteryData(data)) {
                                    CrossDevice.sendRemotePacket(data)
                                    CrossDevice.batteryBytes = data
                                    batteryNotification.setBattery(data)
                                    sendBroadcast(Intent(AirPodsNotifications.Companion.BATTERY_DATA).apply {
                                        putParcelableArrayListExtra(
                                            "data",
                                            ArrayList(batteryNotification.getBattery())
                                        )
                                    })
                                    updateBatteryWidget()
                                    updateNotificationContent(
                                        true,
                                        this@AirPodsService.getSharedPreferences(
                                            "settings",
                                            MODE_PRIVATE
                                        ).getString("name", device.name),
                                        batteryNotification.getBattery()
                                    )
                                    for (battery in batteryNotification.getBattery()) {
                                        Log.d(
                                            "AirPods Parser",
                                            "${battery.getComponentName()}: ${battery.getStatusName()} at ${battery.level}% "
                                        )
                                    }
                                    if (batteryNotification.getBattery()[0].status == 1 && batteryNotification.getBattery()[1].status == 1) {
                                        disconnectAudio(this@AirPodsService, device)
                                    } else {
                                        connectAudio(this@AirPodsService, device)
                                    }
                                } else if (conversationAwarenessNotification.isConversationalAwarenessData(
                                        data
                                    )
                                ) {
                                    conversationAwarenessNotification.setData(data)
                                    sendBroadcast(Intent(AirPodsNotifications.Companion.CA_DATA).apply {
                                        putExtra("data", conversationAwarenessNotification.status)
                                    })


                                    if (conversationAwarenessNotification.status == 1.toByte() || conversationAwarenessNotification.status == 2.toByte()) {
                                        MediaController.startSpeaking()
                                    } else if (conversationAwarenessNotification.status == 8.toByte() || conversationAwarenessNotification.status == 9.toByte()) {
                                        MediaController.stopSpeaking()
                                    }

                                    Log.d(
                                        "AirPods Parser",
                                        "Conversation Awareness: ${conversationAwarenessNotification.status}"
                                    )
                                } else {
                                }
                            }
                        }
                        Log.d("AirPods Service", "Socket closed")
                        isConnectedLocally = false
                        socket.close()
                        sendBroadcast(Intent(AirPodsNotifications.Companion.AIRPODS_DISCONNECTED))
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d("AirPodsService", "Failed to connect to socket")
            }
        }
    }

    fun disconnect() {
        if (!this::socket.isInitialized) return
        socket.close()
        MediaController.pausedForCrossDevice = false
        Log.d("AirPodsService", "Disconnected from AirPods, showing island.")
        showIsland(this, batteryNotification.getBattery().find { it.component == BatteryComponent.LEFT}?.level!!.coerceAtMost(batteryNotification.getBattery().find { it.component == BatteryComponent.RIGHT}?.level!!),
            IslandType.MOVED_TO_REMOTE)
        val bluetoothAdapter = getSystemService(BluetoothManager::class.java).adapter
        bluetoothAdapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    val connectedDevices = proxy.connectedDevices
                    if (connectedDevices.isNotEmpty()) {
                        MediaController.sendPause()
                    }
                }
                bluetoothAdapter.closeProfileProxy(profile, proxy)
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)
        isConnectedLocally = false
        CrossDevice.isAvailable = true
    }

    fun sendPacket(packet: String) {
        val fromHex = packet.split(" ").map { it.toInt(16).toByte() }
        if (!isConnectedLocally && CrossDevice.isAvailable) {
            CrossDevice.sendRemotePacket(CrossDevicePackets.AIRPODS_DATA_HEADER.packet + fromHex.toByteArray())
            return
        }
        if (this::socket.isInitialized && socket.isConnected && socket.outputStream != null) {
            val byteArray = fromHex.toByteArray()
            socket.outputStream?.write(byteArray)
            socket.outputStream?.flush()
            logPacket(byteArray, "Sent")
        }
    }

    fun sendPacket(packet: ByteArray) {
        if (!isConnectedLocally && CrossDevice.isAvailable) {
            CrossDevice.sendRemotePacket(CrossDevicePackets.AIRPODS_DATA_HEADER.packet + packet)
            return
        }
        if (this::socket.isInitialized && socket.isConnected && socket.outputStream != null) {
            socket.outputStream?.write(packet)
            socket.outputStream?.flush()
            logPacket(packet, "Sent")
        }
    }

    fun setANCMode(mode: Int) {
        Log.d("AirPodsService", "setANCMode: $mode")
        when (mode) {
            1 -> {
                sendPacket(Enums.NOISE_CANCELLATION_OFF.value)
            }

            2 -> {
                sendPacket(Enums.NOISE_CANCELLATION_ON.value)
            }

            3 -> {
                sendPacket(Enums.NOISE_CANCELLATION_TRANSPARENCY.value)
            }

            4 -> {
                sendPacket(Enums.NOISE_CANCELLATION_ADAPTIVE.value)
            }
        }
    }

    fun setCAEnabled(enabled: Boolean) {
        sendPacket(if (enabled) Enums.SET_CONVERSATION_AWARENESS_ON.value else Enums.SET_CONVERSATION_AWARENESS_OFF.value)
    }

    fun setOffListeningMode(enabled: Boolean) {
        sendPacket(
            byteArrayOf(
                0x04,
                0x00,
                0x04,
                0x00,
                0x09,
                0x00,
                0x34,
                if (enabled) 0x01 else 0x02,
                0x00,
                0x00,
                0x00
            )
        )
        updateNoiseControlWidget()
    }

    fun setAdaptiveStrength(strength: Int) {
        val bytes =
            byteArrayOf(
                0x04,
                0x00,
                0x04,
                0x00,
                0x09,
                0x00,
                0x2E,
                strength.toByte(),
                0x00,
                0x00,
                0x00
            )
        sendPacket(bytes)
    }

    fun setPressSpeed(speed: Int) {
        // 0x00 = default, 0x01 = slower, 0x02 = slowest
        val bytes =
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x17, speed.toByte(), 0x00, 0x00, 0x00)
        sendPacket(bytes)
    }

    fun setPressAndHoldDuration(speed: Int) {
        // 0 - default, 1 - slower, 2 - slowest
        val bytes =
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x18, speed.toByte(), 0x00, 0x00, 0x00)
        sendPacket(bytes)
    }

    fun setVolumeSwipeSpeed(speed: Int) {
        // 0 - default, 1 - longer, 2 - longest
        val bytes =
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x23, speed.toByte(), 0x00, 0x00, 0x00)
        Log.d(
            "AirPodsService",
            "Setting volume swipe speed to $speed by packet ${
                bytes.joinToString(" ") {
                    "%02X".format(
                        it
                    )
                }
            }"
        )
        sendPacket(bytes)
    }

    fun setNoiseCancellationWithOnePod(enabled: Boolean) {
        val bytes = byteArrayOf(
            0x04,
            0x00,
            0x04,
            0x00,
            0x09,
            0x00,
            0x1B,
            if (enabled) 0x01 else 0x02,
            0x00,
            0x00,
            0x00
        )
        sendPacket(bytes)
    }

    fun setVolumeControl(enabled: Boolean) {
        val bytes = byteArrayOf(
            0x04,
            0x00,
            0x04,
            0x00,
            0x09,
            0x00,
            0x25,
            if (enabled) 0x01 else 0x02,
            0x00,
            0x00,
            0x00
        )
        sendPacket(bytes)
    }

    fun setToneVolume(volume: Int) {
        val bytes =
            byteArrayOf(0x04, 0x00, 0x04, 0x00, 0x09, 0x00, 0x1F, volume.toByte(), 0x50, 0x00, 0x00)
        sendPacket(bytes)
    }

    val earDetectionNotification = AirPodsNotifications.EarDetection()
    val ancNotification = AirPodsNotifications.ANC()
    val batteryNotification = AirPodsNotifications.BatteryNotification()
    val conversationAwarenessNotification =
        AirPodsNotifications.ConversationalAwarenessNotification()

    var earDetectionEnabled = true

    fun setEarDetection(enabled: Boolean) {
        earDetectionEnabled = enabled
    }

    fun getBattery(): List<Battery> {
        if (!isConnectedLocally && CrossDevice.isAvailable) {
            batteryNotification.setBattery(CrossDevice.batteryBytes)
        }
        return batteryNotification.getBattery()
    }

    fun getANC(): Int {
        if (!isConnectedLocally && CrossDevice.isAvailable) {
            ancNotification.setStatus(CrossDevice.ancBytes)
        }
        return ancNotification.status
    }

    fun disconnectAudio(context: Context, device: BluetoothDevice?) {
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter
        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    try {
                        val method =
                            proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method =
                            proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.HEADSET)
    }

    fun connectAudio(context: Context, device: BluetoothDevice?) {
        val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.A2DP) {
                    try {
                        val method =
                            proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.A2DP, proxy)
                        if (MediaController.pausedForCrossDevice) {
                            MediaController.sendPlay()
                        }
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.A2DP)

        bluetoothAdapter?.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                if (profile == BluetoothProfile.HEADSET) {
                    try {
                        val method =
                            proxy.javaClass.getMethod("connect", BluetoothDevice::class.java)
                        method.invoke(proxy, device)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        bluetoothAdapter.closeProfileProxy(BluetoothProfile.HEADSET, proxy)
                    }
                }
            }

            override fun onServiceDisconnected(profile: Int) {}
        }, BluetoothProfile.HEADSET)
    }

    fun setName(name: String) {
        val nameBytes = name.toByteArray()
        val bytes = byteArrayOf(
            0x04, 0x00, 0x04, 0x00, 0x1a, 0x00, 0x01,
            nameBytes.size.toByte(), 0x00
        ) + nameBytes
        sendPacket(bytes)
        val hex = bytes.joinToString(" ") { "%02X".format(it) }
        updateNotificationContent(true, name, batteryNotification.getBattery())
        Log.d("AirPodsService", "setName: $name, sent packet: $hex")
    }

    fun setPVEnabled(enabled: Boolean) {
        var hex = "04 00 04 00 09 00 26 ${if (enabled) "01" else "02"} 00 00 00"
        var bytes = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        sendPacket(bytes)
        hex =
            "04 00 04 00 17 00 00 00 10 00 12 00 08 E${if (enabled) "6" else "5"} 05 10 02 42 0B 08 50 10 02 1A 05 02 ${if (enabled) "32" else "00"} 00 00 00"
        bytes = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        sendPacket(bytes)
    }

    fun setLoudSoundReduction(enabled: Boolean) {
        val hex = "52 1B 00 0${if (enabled) "1" else "0"}"
        val bytes = hex.split(" ").map { it.toInt(16).toByte() }.toByteArray()
        sendPacket(bytes)
    }

    fun findChangedIndex(oldArray: BooleanArray, newArray: BooleanArray): Int {
        for (i in oldArray.indices) {
            if (oldArray[i] != newArray[i]) {
                return i
            }
        }
        throw IllegalArgumentException("No element has changed")
    }

    fun updateLongPress(
        oldLongPressArray: BooleanArray,
        newLongPressArray: BooleanArray,
        offListeningMode: Boolean
    ) {
        if (oldLongPressArray.contentEquals(newLongPressArray)) {
            return
        }
        val oldModes = mutableSetOf<LongPressMode>()
        val newModes = mutableSetOf<LongPressMode>()

        if (oldLongPressArray[0]) oldModes.add(LongPressMode.OFF)
        if (oldLongPressArray[1]) oldModes.add(LongPressMode.ANC)
        if (oldLongPressArray[2]) oldModes.add(LongPressMode.TRANSPARENCY)
        if (oldLongPressArray[3]) oldModes.add(LongPressMode.ADAPTIVE)

        if (newLongPressArray[0]) newModes.add(LongPressMode.OFF)
        if (newLongPressArray[1]) newModes.add(LongPressMode.ANC)
        if (newLongPressArray[2]) newModes.add(LongPressMode.TRANSPARENCY)
        if (newLongPressArray[3]) newModes.add(LongPressMode.ADAPTIVE)

        val changedIndex = findChangedIndex(oldLongPressArray, newLongPressArray)
        val newEnabled = newLongPressArray[changedIndex]

        val packet = determinePacket(changedIndex, newEnabled, oldModes, newModes)
        packet?.let {
            Log.d("AirPodsService", "Sending packet: ${it.joinToString(" ") { "%02X".format(it) }}")
            sendPacket(it)
        }
    }

    override fun onDestroy() {
        clearPacketLogs()
        Log.d("AirPodsService", "Service stopped is being destroyed for some reason!")
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            unregisterReceiver(connectionReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            unregisterReceiver(disconnectionReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        try {
            unregisterReceiver(earReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE)
        // Reset state variables
        isConnectedLocally = false
        CrossDevice.isAvailable = true
        super.onDestroy()
    }
}

private fun Int.dpToPx(): Int {
    val density = Resources.getSystem().displayMetrics.density
    return (this * density).toInt()
}
