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

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import me.kavishdevar.aln.R
import me.kavishdevar.aln.composables.IndependentToggle
import me.kavishdevar.aln.composables.StyledSwitch
import me.kavishdevar.aln.services.ServiceManager
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSettingsScreen(navController: NavController) {
    val sharedPreferences = LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val name = remember { mutableStateOf(sharedPreferences.getString("name", "") ?: "") }
    val isDarkTheme = isSystemInDarkTheme()
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_settings),
                        fontFamily = FontFamily(Font(R.font.sf_pro)),
                    )
                },
                navigationIcon = {
                    TextButton(
                        onClick = {
                            navController.popBackStack()
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = if (isDarkTheme)  Color(0xFF007AFF) else Color(0xFF3C6DF5),
                            modifier = Modifier.scale(1.5f)
                        )
                        Text(
                            text = name.value,
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isDarkTheme) Color(0xFF007AFF) else Color(0xFF3C6DF5),
                                fontFamily = FontFamily(Font(R.font.sf_pro))
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = if (isSystemInDarkTheme()) Color(0xFF000000)
        else Color(0xFFF2F2F7),
    ) { paddingValues ->
        Column (
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp)
        ) {
            val isDarkTheme = isSystemInDarkTheme()

            val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
            val textColor = if (isDarkTheme) Color.White else Color.Black

            IndependentToggle("Show phone battery in widget", ServiceManager.getService()!!, "setPhoneBatteryInWidget", sharedPreferences)

            Column (
                modifier = Modifier
                    .fillMaxWidth()
                    .height(275.sp.value.dp)
                    .background(
                        backgroundColor,
                        RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                val sliderValue = remember { mutableFloatStateOf(0f) }
                LaunchedEffect(sliderValue) {
                    if (sharedPreferences.contains("conversational_awareness_volume")) {
                        sliderValue.floatValue = sharedPreferences.getInt("conversational_awareness_volume", 0).toFloat()
                    }
                }
                LaunchedEffect(sliderValue.floatValue) {
                    sharedPreferences.edit().putInt("conversational_awareness_volume", sliderValue.floatValue.toInt()).apply()
                }

                val trackColor = if (isDarkTheme) Color(0xFFB3B3B3) else Color(0xFFD9D9D9)
                val thumbColor = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFFFFFFFF)
                val labelTextColor = if (isDarkTheme) Color.White else Color.Black

                Text(
                    text = stringResource(R.string.conversational_awareness_customization),
                    style = TextStyle(
                        fontSize = 20.sp,
                        color = textColor
                    ),
                    modifier = Modifier
                        .padding(top = 12.dp, bottom = 4.dp)
                )


                var conversationalAwarenessPauseMusicEnabled by remember {
                    mutableStateOf(
                        sharedPreferences.getBoolean("conversational_awareness_pause_music", true)
                    )
                }

                fun updateConversationalAwarenessPauseMusic(enabled: Boolean) {
                    conversationalAwarenessPauseMusicEnabled = enabled
                    sharedPreferences.edit().putBoolean("conversational_awareness_pause_music", enabled).apply()
                }

                var relativeConversationalAwarenessVolumeEnabled by remember {
                    mutableStateOf(
                        sharedPreferences.getBoolean("relative_conversational_awareness_volume", true)
                    )
                }

                fun updateRelativeConversationalAwarenessVolume(enabled: Boolean) {
                    relativeConversationalAwarenessVolumeEnabled = enabled
                    sharedPreferences.edit().putBoolean("relative_conversational_awareness_volume", enabled).apply()
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.sp.value.dp)
                        .background(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Transparent
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            updateConversationalAwarenessPauseMusic(!conversationalAwarenessPauseMusicEnabled)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.conversational_awareness_pause_music),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.conversational_awareness_pause_music_description),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = conversationalAwarenessPauseMusicEnabled,
                        onCheckedChange = {
                            updateConversationalAwarenessPauseMusic(it)
                        },
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(85.sp.value.dp)
                        .background(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.Transparent
                        )
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) {
                            updateRelativeConversationalAwarenessVolume(!relativeConversationalAwarenessVolumeEnabled)
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.relative_conversational_awareness_volume),
                            fontSize = 16.sp,
                            color = textColor
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.relative_conversational_awareness_volume_description),
                            fontSize = 14.sp,
                            color = textColor.copy(0.6f),
                            lineHeight = 16.sp,
                        )
                    }

                    StyledSwitch(
                        checked = relativeConversationalAwarenessVolumeEnabled,
                        onCheckedChange = {
                            updateRelativeConversationalAwarenessVolume(it)
                        }
                    )
                }

                val activeTrackColor = if (isDarkTheme) Color(0xFF007AFF) else Color(0xFF3C6DF5)

                Slider(
                    value = sliderValue.floatValue,
                    onValueChange = {
                        sliderValue.floatValue = it
                    },
                    valueRange = 10f..85f,
                    onValueChangeFinished = {
                        sliderValue.floatValue = sliderValue.floatValue.roundToInt().toFloat()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = thumbColor,
                        activeTrackColor = activeTrackColor,
                        inactiveTrackColor = trackColor,
                    ),
                    thumb = {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .shadow(4.dp, CircleShape)
                                .background(thumbColor, CircleShape)
                        )
                    },
                    track = {
                        Box (
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp),
                            contentAlignment = Alignment.CenterStart
                        )
                        {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .background(trackColor, RoundedCornerShape(4.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(((sliderValue.floatValue - 10) * 100) /7500)
                                    .height(4.dp)
                                    .background(if (conversationalAwarenessPauseMusicEnabled) trackColor else activeTrackColor, RoundedCornerShape(4.dp))
                            )
                        }
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "10%",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            color = labelTextColor
                        ),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                    Text(
                        text = "85%",
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            color = labelTextColor
                        ),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun AppSettingsScreenPreview() {
    AppSettingsScreen(navController = NavController(LocalContext.current))
}