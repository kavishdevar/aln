package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.primex.core.ExperimentalToolkitApi
import me.kavishdevar.aln.ui.theme.ALNTheme

lateinit var serviceConnection: ServiceConnection
lateinit var connectionStatusReceiver: BroadcastReceiver

@ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalToolkitApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ALNTheme {
                getSharedPreferences("settings", MODE_PRIVATE).edit().putLong("textColor",
                    MaterialTheme.colorScheme.onSurface.toArgb().toLong()).apply()
                Main()
                startService(Intent(this, AirPodsService::class.java))
            }
        }
    }
    override fun onDestroy() {
        try {
            unbindService(serviceConnection)
            Log.d("MainActivity", "Unbound service")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error while unbinding service: $e")
        }
        try {
            unregisterReceiver(connectionStatusReceiver)
            Log.d("MainActivity", "Unregistered receiver")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error while unregistering receiver: $e")
        }
        super.onDestroy()
    }

    override fun onStop() {
        try {
            unbindService(serviceConnection)
            Log.d("MainActivity", "Unbound service")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error while unbinding service: $e")
        }
        try {
            unregisterReceiver(connectionStatusReceiver)
            Log.d("MainActivity", "Unregistered receiver")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error while unregistering receiver: $e")
        }
        super.onStop()
    }
}

@SuppressLint("MissingPermission", "InlinedApi")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Main() {
    val isConnected = remember { mutableStateOf(false) }
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.POST_NOTIFICATIONS"
        )
    )

    val airPodsService = remember { mutableStateOf<AirPodsService?>(null) }
    if (permissionState.allPermissionsGranted) {
        val context = LocalContext.current
        val navController = rememberNavController()

        connectionStatusReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AirPodsNotifications.AIRPODS_CONNECTED) {
                    Log.d("MainActivity", "AirPods Connected intent received")
                    isConnected.value = true
                }
                else if (intent.action == AirPodsNotifications.AIRPODS_DISCONNECTED) {
                    Log.d("MainActivity", "AirPods Disconnected intent received")
                    isConnected.value = false
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(AirPodsNotifications.AIRPODS_CONNECTED)
            addAction(AirPodsNotifications.AIRPODS_DISCONNECTED)
            addAction(AirPodsNotifications.AIRPODS_CONNECTION_DETECTED)
        }
        Log.d("MainActivity", "Registering Receiver")
        context.registerReceiver(connectionStatusReceiver, filter, Context.RECEIVER_EXPORTED)
        Log.d("MainActivity", "Registered Receiver")

        NavHost(
            navController = navController,
            startDestination = "settings",
            enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
            exitTransition = { slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) },
            popEnterTransition = { slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) },
            popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
        ) {
            composable("settings") {
                if (airPodsService.value != null) {
                    AirPodsSettingsScreen(
                        device = airPodsService.value?.device,
                        service = airPodsService.value!!,
                        navController = navController,
                        isConnected = isConnected.value
                    )
                }
            }
            composable("debug") {
                DebugScreen(navController = navController)
            }
        }

         serviceConnection = remember {
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val binder = service as AirPodsService.LocalBinder
                    airPodsService.value = binder.getService()
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    airPodsService.value = null
                }
            }
        }

        context.bindService(Intent(context, AirPodsService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)

        if (airPodsService.value?.isConnected == true) {
            isConnected.value = true
        }
    } else {
        // Permission is not granted, request it
        Column (
            modifier = Modifier.padding(24.dp),
        ){
            val textToShow = if (permissionState.shouldShowRationale) {
                // If the user has denied the permission but not permanently, explain why it's needed.
                "Please enable Bluetooth and Notification permissions to use the app. The Nearby Devices is required to connect to your AirPods, and the notification is required to show the AirPods battery status."
            } else {
                // If the user has permanently denied the permission, inform them to enable it in settings.
                "Please enable Bluetooth and Notification permissions in the app settings to use the app."
            }
            Text(textToShow)
            Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                Text("Request permission")
            }
        }
    }
}