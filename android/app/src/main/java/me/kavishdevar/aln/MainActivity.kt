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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.primex.core.ExperimentalToolkitApi
import me.kavishdevar.aln.ui.theme.ALNTheme


@ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalToolkitApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ALNTheme {
                Main()
                startService(Intent(this, AirPodsService::class.java))
            }
        }
    }
}

@SuppressLint("MissingPermission", "InlinedApi")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Main() {
    val bluetoothConnectPermissionState = rememberPermissionState(
        permission = "android.permission.BLUETOOTH_CONNECT"
    )

    if (bluetoothConnectPermissionState.status.isGranted) {
        val context = LocalContext.current
        val airPodsService = remember { mutableStateOf<AirPodsService?>(null) }
        val navController = rememberNavController()


        val disconnectReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                Log.d("MainActivity", "Received DISCONNECTED broadcast")
                navController.navigate("notConnected")
            }
        }

        context.registerReceiver(disconnectReceiver, IntentFilter(AirPodsNotifications.AIRPODS_DISCONNECTED),
            Context.RECEIVER_NOT_EXPORTED)

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
                    device = airPodsService.value?.device,
                    service = airPodsService.value,
                    navController = navController
                )
            }
            composable("debug") {
                DebugScreen(navController = navController)
            }
        }

        val receiver = object: BroadcastReceiver() {
            override fun onReceive(p0: Context?, p1: Intent?) {
                navController.navigate("settings")
                navController.popBackStack("notConnected", inclusive = true)
            }
        }

        context.registerReceiver(receiver, IntentFilter(AirPodsNotifications.AIRPODS_CONNECTED),
            Context.RECEIVER_EXPORTED)

        val serviceConnection = remember {
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

        val alreadyConnected = remember { mutableStateOf(false) }
        if (airPodsService.value?.isConnected == true && !alreadyConnected.value) {
            Log.d("ALN", "Connected")
            navController.navigate("settings")
        } else {
            Log.d("ALN", "Not connected")
            navController.navigate("notConnected")
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