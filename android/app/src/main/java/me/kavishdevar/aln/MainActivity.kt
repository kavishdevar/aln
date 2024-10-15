package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat.getSystemService
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import me.kavishdevar.aln.ui.theme.ALNTheme

@ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val topAppBarTitle = remember { mutableStateOf("AirPods Pro") }
            ALNTheme {
                Scaffold (
                    containerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color(
                        0xFF000000
                    ) else Color(
                        0xFFF2F2F7
                    ),
                    topBar = {
                        CenterAlignedTopAppBar(
                            title = {
                                Text(
                                    text = topAppBarTitle.value,
                                    color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color.White else Color.Black,
                                )
                            },
                            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                containerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color(
                                    0xFF000000
                                ) else Color(
                                    0xFFF2F2F7
                                ),
                            )
                        )
                    }
                ) { innerPadding ->
                    Main(innerPadding, topAppBarTitle)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Main(paddingValues: PaddingValues, topAppBarTitle: MutableState<String>) {
    val bluetoothConnectPermissionState = rememberPermissionState(
        permission = "android.permission.BLUETOOTH_CONNECT"
    )

    if (bluetoothConnectPermissionState.status.isGranted) {
        val context = LocalContext.current
        val uuid: ParcelUuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")
        val bluetoothManager = getSystemService(context, BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
        val airpodsDevice = remember { mutableStateOf<BluetoothDevice?>(null) }
        val airPodsService = remember { mutableStateOf<AirPodsService?>(null) }
        val navController = rememberNavController()

        val disconnectReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                navController.navigate("notConnected")
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(disconnectReceiver, IntentFilter(AirPodsNotifications.AIRPODS_DISCONNECTED),
                Context.RECEIVER_NOT_EXPORTED)
        }

        // Service connection for AirPodsService
        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val binder = service as AirPodsService.LocalBinder
                airPodsService.value = binder.getService()
                Log.d("AirPodsService", "Service connected")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                airPodsService.value = null
            }
        }

        // Function to check if AirPods are connected
        fun checkIfAirPodsConnected() {
            val devices = bluetoothAdapter?.bondedDevices
            devices?.forEach { device ->
                if (device.uuids.contains(uuid)) {
                    bluetoothAdapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                            if (profile == BluetoothProfile.A2DP) {
                                val connectedDevices = proxy.connectedDevices
                                if (connectedDevices.isNotEmpty()) {
                                    airpodsDevice.value = device
                                    val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                                    topAppBarTitle.value = sharedPreferences.getString("name", device.name) ?: device.name
                                    // Start AirPods service if not running
                                    if (context.getSystemService(AirPodsService::class.java)?.isRunning != true) {
                                        context.startService(Intent(context, AirPodsService::class.java).apply {
                                            putExtra("device", device)
                                        })
                                        context.bindService(Intent(context, AirPodsService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
                                    }
                                } else {
                                    airpodsDevice.value = null
                                }
                            }
                            bluetoothAdapter.closeProfileProxy(profile, proxy)
                        }

                        override fun onServiceDisconnected(profile: Int) {}
                    }, BluetoothProfile.A2DP)
                }
            }
        }

        // BroadcastReceiver to listen for connection state changes
        val bluetoothReceiver = remember {
            object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action
                    val device = intent?.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    if (action == BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED) {
                        when (intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1)) {
                            BluetoothAdapter.STATE_CONNECTED -> {
                                if (device?.uuids?.contains(uuid) == true) {
                                    airpodsDevice.value = device
                                    checkIfAirPodsConnected()
                                }
                            }
                            BluetoothAdapter.STATE_DISCONNECTED -> {
                                if (device?.uuids?.contains(uuid) == true) {
                                    airpodsDevice.value = null
                                    // Show not connected screen when AirPods disconnect
                                    navController.navigate("notConnected")
                                }
                            }
                        }
                    }
                }
            }
        }

        // Register the receiver in LaunchedEffect
        LaunchedEffect(Unit) {
            val filter = IntentFilter().apply {
                addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            }

            // Initial check for AirPods connection
            checkIfAirPodsConnected()
        }

        // UI logic
        NavHost(
            navController = navController,
            startDestination = "notConnected",
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
        ) {
            composable("notConnected") {
                Text("Not Connected...")
            }
            composable("settings") {
                AirPodsSettingsScreen(
                    paddingValues,
                    airpodsDevice.value,
                    service = airPodsService.value,
                    navController = navController
                )
            }
            composable("debug") {
                DebugScreen(navController = navController)
            }
        }

        // Automatically navigate to settings screen if AirPods are connected
        if (airpodsDevice.value != null) {
            LaunchedEffect(Unit) {
                navController.navigate("settings") {
                    popUpTo("notConnected") { inclusive = true }
                }
            }
        } else {
            Text("No AirPods connected")
        }
    } else {
        // Permission is not granted, request it
        Column (
            modifier = Modifier.padding(24.dp),
        ){
            val textToShow = if (bluetoothConnectPermissionState.status.shouldShowRationale) {
                // If the user has denied the permission but not permanently, explain why it's needed.
                "The BLUETOOTH_CONNECT permission is important for this app. Please grant it to proceed."
            } else {
                // If the user has permanently denied the permission, inform them to enable it in settings.
                "BLUETOOTH_CONNECT permission required for this feature. Please enable it in settings."
            }
            Text(textToShow)
            Button(onClick = { bluetoothConnectPermissionState.launchPermissionRequest() }) {
                Text("Request permission")
            }
        }
    }
}

@PreviewLightDark
@Composable
fun PreviewAirPodsSettingsScreen() {
    AirPodsSettingsScreen(paddingValues = PaddingValues(0.dp), device = null, service = null, navController = rememberNavController())
}
