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

package me.kavishdevar.aln.screens

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import me.kavishdevar.aln.R
import me.kavishdevar.aln.composables.AccessibilitySettings
import me.kavishdevar.aln.composables.AudioSettings
import me.kavishdevar.aln.composables.BatteryView
import me.kavishdevar.aln.composables.IndependentToggle
import me.kavishdevar.aln.composables.NameField
import me.kavishdevar.aln.composables.NavigationButton
import me.kavishdevar.aln.composables.NoiseControlSettings
import me.kavishdevar.aln.composables.PressAndHoldSettings
import me.kavishdevar.aln.services.AirPodsService
import me.kavishdevar.aln.ui.theme.ALNTheme
import me.kavishdevar.aln.utils.AirPodsNotifications

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun AirPodsSettingsScreen(dev: BluetoothDevice?, service: AirPodsService,
                          navController: NavController, isConnected: Boolean, isRemotelyConnected: Boolean) {
    val sharedPreferences = LocalContext.current.getSharedPreferences("settings", MODE_PRIVATE)
    var device by remember { mutableStateOf(dev) }
    var deviceName by remember {
        mutableStateOf(
            TextFieldValue(
                sharedPreferences.getString("name", device?.name ?: "AirPods Pro").toString()
            )
        )
    }

    val nameChangeListener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "name") {
                deviceName = TextFieldValue(sharedPreferences.getString("name", "AirPods Pro").toString())
            }
        }
    }

    DisposableEffect(Unit) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(nameChangeListener)
        onDispose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(nameChangeListener)
        }
    }


    val verticalScrollState  = rememberScrollState()
    val hazeState = remember { HazeState() }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    Scaffold(
        containerColor = if (isSystemInDarkTheme()) Color(
            0xFF000000
        ) else Color(
            0xFFF2F2F7
        ),
        topBar = {
            val darkMode = isSystemInDarkTheme()
            val mDensity = remember { mutableFloatStateOf(1f) }
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = deviceName.text,
                            style = TextStyle(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (darkMode) Color.White else Color.Black,
                                fontFamily = FontFamily(Font(R.font.sf_pro))
                            )
                        )
                    },
                    modifier = Modifier
                        .hazeChild(
                            state = hazeState,
                            style = CupertinoMaterials.thick(),
                            block = {
                                alpha =
                                    if (verticalScrollState.value > 55.dp.value * mDensity.floatValue) 1f else 0f
                            }
                        )
                        .drawBehind {
                            mDensity.floatValue = density
                            val strokeWidth = 0.7.dp.value * density
                            val y = size.height - strokeWidth / 2
                            if (verticalScrollState.value > 55.dp.value * density) {
                                drawLine(
                                    if (darkMode) Color.DarkGray else Color.LightGray,
                                    Offset(0f, y),
                                    Offset(size.width, y),
                                    strokeWidth
                                )
                            }
                        },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    actions = {
                        IconButton(
                            onClick = {
                                navController.navigate("app_settings")
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = if (isSystemInDarkTheme()) Color.White else Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                            )
                        }
                    }
                )
        }
    ) { paddingValues ->
        if (isConnected == true || isRemotelyConnected == true) {
            Column(
                modifier = Modifier
                    .haze(hazeState)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(
                        state = verticalScrollState,
                        enabled = true,
                    )
            ) {
                Spacer(Modifier.height(75.dp))
                LaunchedEffect(service) {
                    service.let {
                        it.sendBroadcast(Intent(AirPodsNotifications.Companion.BATTERY_DATA).apply {
                            putParcelableArrayListExtra("data", ArrayList(it.getBattery()))
                        })
                        it.sendBroadcast(Intent(AirPodsNotifications.Companion.ANC_DATA).apply {
                            putExtra("data", it.getANC())
                        })
                    }
                }
                val sharedPreferences = LocalContext.current.getSharedPreferences("settings", MODE_PRIVATE)

                Spacer(modifier = Modifier.height(64.dp))

                BatteryView(service = service)

                Spacer(modifier = Modifier.height(32.dp))

                NameField(
                    name = stringResource(R.string.name),
                    value = deviceName.text,
                    navController = navController
                )

                Spacer(modifier = Modifier.height(32.dp))
                NoiseControlSettings(service = service)

                Spacer(modifier = Modifier.height(16.dp))
                PressAndHoldSettings(navController = navController)

                Spacer(modifier = Modifier.height(16.dp))
                AudioSettings(service = service, sharedPreferences = sharedPreferences)

                Spacer(modifier = Modifier.height(16.dp))
                IndependentToggle(
                    name = "Automatic Ear Detection",
                    service = service,
                    functionName = "setEarDetection",
                    sharedPreferences = sharedPreferences,
                    true
                )

                Spacer(modifier = Modifier.height(16.dp))
                IndependentToggle(
                    name = "Off Listening Mode",
                    service = service,
                    functionName = "setOffListeningMode",
                    sharedPreferences = sharedPreferences,
                    false
                )

                Spacer(modifier = Modifier.height(16.dp))
                AccessibilitySettings(service = service, sharedPreferences = sharedPreferences)

                Spacer(modifier = Modifier.height(16.dp))
                NavigationButton("debug", "Debug", navController)
                Spacer(Modifier.height(24.dp))
            }
        }
        else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .verticalScroll(
                        state = verticalScrollState,
                        enabled = true,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "AirPods not connected",
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                        fontFamily = FontFamily(Font(R.font.sf_pro))
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Please connect your AirPods to access settings. If you're stuck here, then try reopening the app again after closing it from the recents.\n(DO NOT KILL THE APP!)",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                        fontFamily = FontFamily(Font(R.font.sf_pro))
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}


@Preview
@Composable
fun AirPodsSettingsScreenPreview() {
    Column (
        modifier = Modifier.height(2000.dp)
    ) {
        ALNTheme (
            darkTheme = true
        ) {
            AirPodsSettingsScreen(dev = null, service = AirPodsService(), navController = rememberNavController(), isConnected = true, isRemotelyConnected = false)
        }
    }
}