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
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import me.kavishdevar.aln.screens.AirPodsSettingsScreen
import me.kavishdevar.aln.screens.AppSettingsScreen
import me.kavishdevar.aln.screens.DebugScreen
import me.kavishdevar.aln.screens.LongPress
import me.kavishdevar.aln.screens.RenameScreen
import me.kavishdevar.aln.services.AirPodsService
import me.kavishdevar.aln.ui.theme.ALNTheme
import me.kavishdevar.aln.utils.AirPodsNotifications
import me.kavishdevar.aln.utils.CrossDevice

lateinit var serviceConnection: ServiceConnection
lateinit var connectionStatusReceiver: BroadcastReceiver

@ExperimentalMaterial3Api
class MainActivity : ComponentActivity() {
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
        sendBroadcast(Intent(AirPodsNotifications.DISCONNECT_RECEIVERS))
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

@SuppressLint("MissingPermission", "InlinedApi", "UnspecifiedRegisterReceiverFlag")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Main() {
    val isConnected = remember { mutableStateOf(false) }
    val isRemotelyConnected = remember { mutableStateOf(false) }
    val permissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            "android.permission.BLUETOOTH_CONNECT",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.READ_PHONE_STATE"
        )
    )
    val airPodsService = remember { mutableStateOf<AirPodsService?>(null) }
    if (permissionState.allPermissionsGranted) {
        val context = LocalContext.current
        val navController = rememberNavController()

        val sharedPreferences = context.getSharedPreferences("settings", MODE_PRIVATE)
        val isAvailableChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "CrossDeviceIsAvailable") {
                Log.d("MainActivity", "CrossDeviceIsAvailable changed")
                isRemotelyConnected.value = sharedPreferences.getBoolean("CrossDeviceIsAvailable", false)
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(isAvailableChangeListener)
        Log.d("MainActivity", "CrossDeviceIsAvailable: ${sharedPreferences.getBoolean("CrossDeviceIsAvailable", false)} | isAvailable: ${CrossDevice.isAvailable}")
        isRemotelyConnected.value = sharedPreferences.getBoolean("CrossDeviceIsAvailable", false) || CrossDevice.isAvailable
        Log.d("MainActivity", "isRemotelyConnected: ${isRemotelyConnected.value}")
        Box (
            modifier = Modifier
                .padding(0.dp)
                .fillMaxSize()
                .background(if (isSystemInDarkTheme()) Color.Black else Color(0xFFF2F2F7))
        ) {
            NavHost(
                navController = navController,
                startDestination = "settings",
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + scaleIn(
                        initialScale = 0.85f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { -it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + scaleOut(
                        targetScale = 0.85f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + scaleIn(
                        initialScale = 0.85f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { it },
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    ) + scaleOut(
                        targetScale = 0.85f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                }
            ) {
                composable("settings") {
                    if (airPodsService.value != null) {
                        AirPodsSettingsScreen(
                            dev = airPodsService.value?.device,
                            service = airPodsService.value!!,
                            navController = navController,
                            isConnected = isConnected.value,
                            isRemotelyConnected = isRemotelyConnected.value
                        )
                    }
                }
                composable("debug") {
                    DebugScreen(navController = navController)
                }
                composable("long_press/{bud}") { navBackStackEntry ->
                    LongPress(
                        navController = navController,
                        name = navBackStackEntry.arguments?.getString("bud")!!
                    )
                }
                composable("rename") { navBackStackEntry ->
                    RenameScreen(navController)
                }
                composable("app_settings") {
                    AppSettingsScreen(navController)
                }
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

        if (airPodsService.value?.isConnectedLocally == true) {
            isConnected.value = true
        }
    } else {
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
