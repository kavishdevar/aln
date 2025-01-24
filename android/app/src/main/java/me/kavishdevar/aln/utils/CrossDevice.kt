package me.kavishdevar.aln.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.SharedPreferences
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.kavishdevar.aln.services.ServiceManager
import java.io.IOException
import java.util.UUID

enum class CrossDevicePackets(val packet: ByteArray) {
    AIRPODS_CONNECTED(byteArrayOf(0x00, 0x01, 0x00, 0x01)),
    AIRPODS_DISCONNECTED(byteArrayOf(0x00, 0x01, 0x00, 0x00)),
    REQUEST_DISCONNECT(byteArrayOf(0x00, 0x02, 0x00, 0x00)),
    REQUEST_BATTERY_BYTES(byteArrayOf(0x00, 0x02, 0x00, 0x01)),
    REQUEST_ANC_BYTES(byteArrayOf(0x00, 0x02, 0x00, 0x02)),
    REQUEST_CONNECTION_STATUS(byteArrayOf(0x00, 0x02, 0x00, 0x03)),
    AIRPODS_DATA_HEADER(byteArrayOf(0x00, 0x04, 0x00, 0x01)),
}


object CrossDevice {
    var initialized = false
    private val uuid = UUID.fromString("1abbb9a4-10e4-4000-a75c-8953c5471342")
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var bluetoothLeAdvertiser: BluetoothLeAdvertiser
    private const val MANUFACTURER_ID = 0x1234
    private const val MANUFACTURER_DATA = "ALN_AirPods"
    var isAvailable: Boolean = false // set to true when airpods are connected to another device
    var batteryBytes: ByteArray = byteArrayOf()
    var ancBytes: ByteArray = byteArrayOf()
    private lateinit var sharedPreferences: SharedPreferences
    private const val packetLogKey = "packet_log"

    @SuppressLint("MissingPermission")
    fun init(context: Context) {
        Log.d("AirPodsQuickSwitchService", "Initializing CrossDevice")
        sharedPreferences = context.getSharedPreferences("packet_logs", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("CrossDeviceIsAvailable", false).apply()
        this.bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter
        this.bluetoothLeAdvertiser = bluetoothAdapter.bluetoothLeAdvertiser
        startAdvertising()
        startServer()
        initialized = true
    }

    @SuppressLint("MissingPermission")
    fun startServer() {
        serverSocket = bluetoothAdapter.listenUsingRfcommWithServiceRecord("ALNCrossDevice", uuid)
        Log.d("AirPodsQuickSwitchService", "Server started")
        CoroutineScope(Dispatchers.IO).launch {
            while (serverSocket != null) {
                try {
                    val socket = serverSocket!!.accept()
                    handleClientConnection(socket)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising() {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addManufacturerData(MANUFACTURER_ID, MANUFACTURER_DATA.toByteArray())
            .addServiceUuid(ParcelUuid(uuid))
            .build()

        bluetoothLeAdvertiser.startAdvertising(settings, data, advertiseCallback)
        Log.d("AirPodsQuickSwitchService", "BLE Advertising started")
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d("AirPodsQuickSwitchService", "BLE Advertising started successfully")
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e("AirPodsQuickSwitchService", "BLE Advertising failed with error code: $errorCode")
        }
    }

    fun setAirPodsConnected(connected: Boolean) {
        if (connected) {
            isAvailable = false
            sharedPreferences.edit().putBoolean("CrossDeviceIsAvailable", false).apply()
            clientSocket?.outputStream?.write(CrossDevicePackets.AIRPODS_CONNECTED.packet)
        } else {
            clientSocket?.outputStream?.write(CrossDevicePackets.AIRPODS_DISCONNECTED.packet)
        }
    }

    fun sendReceivedPacket(packet: ByteArray) {
        Log.d("AirPodsQuickSwitchService", "Sending packet to remote device")
        if (clientSocket == null) {
            Log.d("AirPodsQuickSwitchService", "Client socket is null")
            return
        }
        clientSocket?.outputStream?.write(CrossDevicePackets.AIRPODS_DATA_HEADER.packet + packet)
    }

    private fun logPacket(packet: ByteArray, source: String) {
        val packetHex = packet.joinToString(" ") { "%02X".format(it) }
        val logEntry = "$source: $packetHex"
        val logs = sharedPreferences.getStringSet(packetLogKey, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        logs.add(logEntry)
        sharedPreferences.edit().putStringSet(packetLogKey, logs).apply()
    }

    @SuppressLint("MissingPermission")
    private fun handleClientConnection(socket: BluetoothSocket) {
        Log.d("AirPodsQuickSwitchService", "Client connected")
        clientSocket = socket
        val inputStream = socket.inputStream
        val buffer = ByteArray(1024)
        var bytes: Int
        setAirPodsConnected(ServiceManager.getService()?.isConnectedLocally == true)
        while (true) {
            bytes = inputStream.read(buffer)
            val packet = buffer.copyOf(bytes)
            logPacket(packet, "Relay")
            Log.d("AirPodsQuickSwitchService", "Received packet: ${packet.joinToString("") { "%02x".format(it) }}")
            if (bytes == -1) {
                break
            } else if (packet.contentEquals(CrossDevicePackets.REQUEST_DISCONNECT.packet)) {
                ServiceManager.getService()?.disconnect()
            } else if (packet.contentEquals(CrossDevicePackets.AIRPODS_CONNECTED.packet)) {
                isAvailable = true
                sharedPreferences.edit().putBoolean("CrossDeviceIsAvailable", true).apply()
            } else if (packet.contentEquals(CrossDevicePackets.AIRPODS_DISCONNECTED.packet)) {
                isAvailable = false
                sharedPreferences.edit().putBoolean("CrossDeviceIsAvailable", false).apply()
            } else if (packet.contentEquals(CrossDevicePackets.REQUEST_BATTERY_BYTES.packet)) {
                Log.d("AirPodsQuickSwitchService", "Received battery request, battery data: ${batteryBytes.joinToString("") { "%02x".format(it) }}")
                sendRemotePacket(batteryBytes)
            } else if (packet.contentEquals(CrossDevicePackets.REQUEST_ANC_BYTES.packet)) {
                Log.d("AirPodsQuickSwitchService", "Received ANC request")
                sendRemotePacket(ancBytes)
            } else if (packet.contentEquals(CrossDevicePackets.REQUEST_CONNECTION_STATUS.packet)) {
                Log.d("AirPodsQuickSwitchService", "Received connection status request")
                sendRemotePacket(if (ServiceManager.getService()?.isConnectedLocally == true) CrossDevicePackets.AIRPODS_CONNECTED.packet else CrossDevicePackets.AIRPODS_DISCONNECTED.packet)
            }
            else {
                if (packet.sliceArray(0..3).contentEquals(CrossDevicePackets.AIRPODS_DATA_HEADER.packet)) {
                    isAvailable = true
                    sharedPreferences.edit().putBoolean("CrossDeviceIsAvailable", true).apply()
                    val trimmedPacket = packet.drop(CrossDevicePackets.AIRPODS_DATA_HEADER.packet.size).toByteArray()
                    Log.d("AirPodsQuickSwitchService", "Received relayed packet, with ${sharedPreferences.getBoolean("CrossDeviceIsAvailable", false)} | ${ServiceManager.getService()?.batteryNotification?.isBatteryData(trimmedPacket)}")
                    Log.d("AirPodsQuickSwitchService", "Received relayed packet: ${trimmedPacket.joinToString("") { "%02x".format(it) }}")
                    if (ServiceManager.getService()?.isConnectedLocally == true) {
                        val packetInHex = trimmedPacket.joinToString("") { "%02x".format(it) }
                        ServiceManager.getService()?.sendPacket(packetInHex)
                    } else if (ServiceManager.getService()?.batteryNotification?.isBatteryData(trimmedPacket) == true) {
                        batteryBytes = trimmedPacket
                        ServiceManager.getService()?.batteryNotification?.setBattery(trimmedPacket)
                        Log.d("AirPodsQuickSwitchService", "Battery data: ${ServiceManager.getService()?.batteryNotification?.getBattery()[0]?.level}")
                        ServiceManager.getService()?.updateBatteryWidget()
                        ServiceManager.getService()?.sendBatteryBroadcast()
                        ServiceManager.getService()?.sendBatteryNotification()
                    } else if (ServiceManager.getService()?.ancNotification?.isANCData(trimmedPacket) == true) {
                        ServiceManager.getService()?.ancNotification?.setStatus(trimmedPacket)
                        ServiceManager.getService()?.sendANCBroadcast()
                        ancBytes = trimmedPacket
                    }
                }
            }
        }
    }

    fun sendRemotePacket(byteArray: ByteArray) {
        if (clientSocket == null) {
            Log.d("AirPodsQuickSwitchService", "Client socket is null")
            return
        }
        clientSocket?.outputStream?.write(byteArray)
        clientSocket?.outputStream?.flush()
        logPacket(byteArray, "Sent")
        Log.d("AirPodsQuickSwitchService", "Sent packet to remote device")
    }

    fun checkAirPodsConnectionStatus(): Boolean {
        Log.d("AirPodsQuickSwitchService", "Checking AirPods connection status")
        if (clientSocket == null) {
            Log.d("AirPodsQuickSwitchService", "Client socket is null - linux probably not connected.")
            return false
        }
        return try {
            clientSocket?.outputStream?.write(CrossDevicePackets.REQUEST_CONNECTION_STATUS.packet)
            val buffer = ByteArray(1024)
            val bytes = clientSocket?.inputStream?.read(buffer) ?: -1
            val packet = buffer.copyOf(bytes)
            packet.contentEquals(CrossDevicePackets.AIRPODS_CONNECTED.packet)
        } catch (e: IOException) {
            Log.e("AirPodsQuickSwitchService", "Error checking connection status", e)
            false
        }
    }
}