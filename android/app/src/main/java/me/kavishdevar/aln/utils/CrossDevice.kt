package me.kavishdevar.aln.utils

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.SharedPreferences
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
    AIRPODS_DATA_HEADER(byteArrayOf(0x00, 0x04, 0x00, 0x01)),
}


object CrossDevice {
    private val uuid = UUID.fromString("1abbb9a4-10e4-4000-a75c-8953c5471342")
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private lateinit var bluetoothAdapter: BluetoothAdapter
    var isAvailable: Boolean = false // set to true when airpods are connected to another device
    var batteryBytes: ByteArray = byteArrayOf()
    var ancBytes: ByteArray = byteArrayOf()
    private lateinit var sharedPreferences: SharedPreferences
    @SuppressLint("MissingPermission")
    fun init(context: Context) {
        Log.d("AirPodsQuickSwitchService", "Initializing CrossDevice")
        sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("CrossDeviceIsAvailable", false).apply()
        this.bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter
        startServer()
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
        clientSocket?.outputStream?.write(CrossDevicePackets.AIRPODS_DATA_HEADER.packet + packet)
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
            }
            else {
                if (packet.sliceArray(0..3).contentEquals(CrossDevicePackets.AIRPODS_DATA_HEADER.packet)) {
                    // the AIRPODS_CONNECTED wasn't sent before
                    isAvailable = true
                    sharedPreferences.edit().putBoolean("CrossDeviceIsAvailable", true).apply()
                    val trimmedPacket =
                        packet.drop(CrossDevicePackets.AIRPODS_DATA_HEADER.packet.size).toByteArray()
                    Log.d("AirPodsQuickSwitchService", "Received relayed packet, with ${sharedPreferences.getBoolean("CrossDeviceIsAvailable", false)} | ${ServiceManager.getService()?.batteryNotification?.isBatteryData(trimmedPacket)}")
                    Log.d(
                        "AirPodsQuickSwitchService",
                        "Relayed packet: ${trimmedPacket.joinToString("") { "%02x".format(it) }}"
                    )
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
        Log.d("AirPodsQuickSwitchService", "Sent packet to remote device")
    }
}