
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
package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.*
import com.google.accompanist.permissions.*
import me.kavishdevar.aln.screens.*
import me.kavishdevar.aln.services.AirPodsService
import me.kavishdevar.aln.ui.theme.ALNTheme
import me.kavishdevar.aln.utils.*

private const val TAG = "MainActivity"
lateinit var serviceConnection: ServiceConnection
lateinit var connectionStatusReceiver: BroadcastReceiver

@ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (RootChecker.hasRootAccess()) {
            if (LibPatcher.deployModule(this)) Log.d(TAG, "Module deployed successfully.")
            else Log.e(TAG, "Module deployment failed.")

            val sharedPreferences = getSharedPreferences("settings", MODE_PRIVATE)
            val permissionsGranted = checkPermissionsGranted()
            // We're removing the reboot requirement, so we only care about permissions
            val shouldStartService = permissionsGranted

            setContent {
                ALNTheme {
                    sharedPreferences.edit().putLong("textColor", MaterialTheme.colorScheme.onSurface.toArgb().toLong()).apply()
                    Main(shouldStartService)
                    if (shouldStartService) startService(Intent(this, AirPodsService::class.java))
                }
            }
        } else {
            setContent { ALNTheme { NoRootScreen() } }
        }
    }

    private fun checkPermissionsGranted(): Boolean {
        val requiredPermissions = listOf(
            "android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE", "android.permission.POST_NOTIFICATIONS",
            "android.permission.READ_PHONE_STATE"
        )
        return requiredPermissions.all { checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }
    }

    override fun onDestroy() {
        try { unbindService(serviceConnection); Log.d(TAG, "Unbound service") }
        catch (e: Exception) { Log.e(TAG, "Error while unbinding service: $e") }
        try { unregisterReceiver(connectionStatusReceiver); Log.d(TAG, "Unregistered receiver") }
        catch (e: Exception) { Log.e(TAG, "Error while unregistering receiver: $e") }
        sendBroadcast(Intent(AirPodsNotifications.DISCONNECT_RECEIVERS))
        super.onDestroy()
    }

    override fun onStop() {
        try { unbindService(serviceConnection); Log.d(TAG, "Unbound service") }
        catch (e: Exception) { Log.e(TAG, "Error while unbinding service: $e") }
        try { unregisterReceiver(connectionStatusReceiver); Log.d(TAG, "Unregistered receiver") }
        catch (e: Exception) { Log.e(TAG, "Error while unregistering receiver: $e") }
        super.onStop()
    }
}

@SuppressLint("MissingPermission", "InlinedApi", "UnspecifiedRegisterReceiverFlag")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Main(shouldStartService: Boolean) {
    val isConnected = remember { mutableStateOf(false) }
    val isRemotelyConnected = remember { mutableStateOf(false) }
    val airPodsService = remember { mutableStateOf<AirPodsService?>(null) }
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf("android.permission.BLUETOOTH_CONNECT", "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE", "android.permission.POST_NOTIFICATIONS",
            "android.permission.READ_PHONE_STATE")
    )

    // Request permissions if not granted
    LaunchedEffect(Unit) {
        if (!permissionState.allPermissionsGranted) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    // Re-check permissions when state changes
    val currentPermissionsGranted = permissionState.allPermissionsGranted
    val forceRecompose = remember { mutableStateOf(false) }

    LaunchedEffect(currentPermissionsGranted) {
        if (currentPermissionsGranted && !shouldStartService) {
            // Force recompose when permissions are granted
            forceRecompose.value = !forceRecompose.value
            // Notify activity to update state if needed
            context.sendBroadcast(Intent("me.kavishdevar.aln.PERMISSIONS_GRANTED"))
        }
    }

    if (shouldStartService || currentPermissionsGranted) {
        val navController = rememberNavController()
        val isAvailableChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "CrossDeviceIsAvailable") {
                Log.d(TAG, "CrossDeviceIsAvailable changed")
                isRemotelyConnected.value = sharedPreferences.getBoolean("CrossDeviceIsAvailable", false)
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(isAvailableChangeListener)

        Log.d(TAG, "CrossDeviceIsAvailable: ${sharedPreferences.getBoolean("CrossDeviceIsAvailable", false)} | isAvailable: ${CrossDevice.isAvailable}")
        isRemotelyConnected.value = sharedPreferences.getBoolean("CrossDeviceIsAvailable", false) || CrossDevice.isAvailable
        Log.d(TAG, "isRemotelyConnected: ${isRemotelyConnected.value}")

        Box(modifier = Modifier.padding(0.dp).fillMaxSize()
            .background(if (isSystemInDarkTheme()) Color.Black else Color(0xFFF2F2F7))) {
            NavHost(navController = navController, startDestination = "settings",
                enterTransition = { slideInHorizontally(initialOffsetX = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) +
                    scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow)) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) +
                    scaleOut(targetScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow)) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) +
                    scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it },
                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)) +
                    scaleOut(targetScale = 0.85f, animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow)) }
            ) {
                composable("settings") {
                    airPodsService.value?.let { service ->
                        AirPodsSettingsScreen(dev = service.device, service = service, navController = navController,
                            isConnected = isConnected.value, isRemotelyConnected = isRemotelyConnected.value)
                    }
                }
                composable("debug") { DebugScreen(navController = navController) }
                composable("long_press/{bud}") { backStackEntry ->
                    LongPress(navController = navController, name = backStackEntry.arguments?.getString("bud")!!)
                }
                composable("rename") { RenameScreen(navController) }
                composable("app_settings") { AppSettingsScreen(navController) }
            }
        }

        // Start service if we haven't already
        LaunchedEffect(Unit) {
            if (currentPermissionsGranted && airPodsService.value == null) {
                context.startService(Intent(context, AirPodsService::class.java))
            }
        }

        serviceConnection = remember {
            object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                    val binder = service as AirPodsService.LocalBinder
                    airPodsService.value = binder.getService()
                }
                override fun onServiceDisconnected(name: ComponentName?) { airPodsService.value = null }
            }
        }

        context.bindService(Intent(context, AirPodsService::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        if (airPodsService.value?.isConnectedLocally == true) isConnected.value = true
    } else {
        // Simplified permissions screen - only shown when permissions aren't granted
        Column(modifier = Modifier.fillMaxSize().background(if (isSystemInDarkTheme()) Color.Black else Color.White)
            .padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            val permissionMessage = if (permissionState.shouldShowRationale) {
                "Please enable Bluetooth and Notification permissions to use the app. The Nearby Devices is required to connect to your AirPods, and the notification is required to show the AirPods battery status."
            } else {
                "Please enable Bluetooth and Notification permissions in the app settings to use the app."
            }

            Text(permissionMessage, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 32.dp))

            Button(onClick = { permissionState.launchMultiplePermissionRequest() }) {
                Text("Request Permissions")
            }
        }
    }
}
