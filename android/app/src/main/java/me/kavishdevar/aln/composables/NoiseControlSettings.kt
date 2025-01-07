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

package me.kavishdevar.aln.composables

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.aln.R
import me.kavishdevar.aln.services.AirPodsService
import me.kavishdevar.aln.utils.AirPodsNotifications
import me.kavishdevar.aln.utils.NoiseControlMode

@SuppressLint("UnspecifiedRegisterReceiverFlag")
@Composable
fun NoiseControlSettings(service: AirPodsService) {
    val context = LocalContext.current
    val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val offListeningMode = remember { mutableStateOf(sharedPreferences.getBoolean("off_listening_mode", true)) }
   
    val preferenceChangeListener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "off_listening_mode") {
                offListeningMode.value = sharedPreferences.getBoolean("off_listening_mode", true)
            }
        }
    }
    
    DisposableEffect(Unit) {
        sharedPreferences.registerOnSharedPreferenceChangeListener(preferenceChangeListener)
        onDispose {
            sharedPreferences.unregisterOnSharedPreferenceChangeListener(preferenceChangeListener)
        }
    }
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFE3E3E8)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val textColorSelected = if (isDarkTheme) Color.White else Color.Black
    val selectedBackground = if (isDarkTheme) Color(0xFF5C5A5F) else Color(0xFFFFFFFF)

    val noiseControlMode = remember { mutableStateOf(NoiseControlMode.OFF) }

    val d1a = remember { mutableFloatStateOf(0f) }
    val d2a = remember { mutableFloatStateOf(0f) }
    val d3a = remember { mutableFloatStateOf(0f) }

    fun onModeSelected(mode: NoiseControlMode, received: Boolean = false) {
        if (!received && !offListeningMode.value && mode == NoiseControlMode.OFF) {
            noiseControlMode.value = NoiseControlMode.ADAPTIVE
        } else {
            noiseControlMode.value = mode
        }
        if (!received) service.setANCMode(mode.ordinal + 1)
        when (noiseControlMode.value) {
            NoiseControlMode.NOISE_CANCELLATION -> {
                d1a.floatValue = 1f
                d2a.floatValue = 1f
                d3a.floatValue = 0f
            }
            NoiseControlMode.OFF -> {
                d1a.floatValue = 0f
                d2a.floatValue = 1f
                d3a.floatValue = 1f
            }
            NoiseControlMode.ADAPTIVE -> {
                d1a.floatValue = 1f
                d2a.floatValue = 0f
                d3a.floatValue = 0f
            }
            NoiseControlMode.TRANSPARENCY -> {
                d1a.floatValue = 0f
                d2a.floatValue = 0f
                d3a.floatValue = 1f
            }
        }
    }

    val noiseControlReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AirPodsNotifications.ANC_DATA) {
                    noiseControlMode.value = NoiseControlMode.entries.toTypedArray()[intent.getIntExtra("data", 3) - 1]
                    onModeSelected(noiseControlMode.value, true)
                } else if (intent.action == AirPodsNotifications.DISCONNECT_RECEIVERS) {
                    try {
                        context.unregisterReceiver(this)
                    } catch (e: IllegalArgumentException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    val noiseControlIntentFilter = IntentFilter().apply {
        addAction(AirPodsNotifications.ANC_DATA)
        addAction(AirPodsNotifications.DISCONNECT_RECEIVERS)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(noiseControlReceiver, noiseControlIntentFilter, Context.RECEIVER_EXPORTED)
    } else {
        context.registerReceiver(noiseControlReceiver, noiseControlIntentFilter)
    }

    Text(
        text = "NOISE CONTROL",
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            color = textColor.copy(alpha = 0.6f)
        ),
        modifier = Modifier.padding(8.dp, bottom = 2.dp)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(75.dp)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor, RoundedCornerShape(14.dp))
            ) {
                if (offListeningMode.value) {
                    NoiseControlButton(
                        icon = ImageBitmap.imageResource(R.drawable.noise_cancellation),
                        onClick = { onModeSelected(NoiseControlMode.OFF) },
                        textColor = if (noiseControlMode.value == NoiseControlMode.OFF) textColorSelected else textColor,
                        backgroundColor = if (noiseControlMode.value == NoiseControlMode.OFF) selectedBackground else Color.Transparent,
                        modifier = Modifier.weight(1f)
                    )
                    VerticalDivider(
                        thickness = 1.dp,
                        modifier = Modifier
                            .padding(vertical = 10.dp)
                            .alpha(d1a.floatValue),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    )
                }
                NoiseControlButton(
                    icon = ImageBitmap.imageResource(R.drawable.transparency),
                    onClick = { onModeSelected(NoiseControlMode.TRANSPARENCY) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.TRANSPARENCY) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.TRANSPARENCY) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .alpha(d2a.floatValue),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                NoiseControlButton(
                    icon = ImageBitmap.imageResource(R.drawable.adaptive),
                    onClick = { onModeSelected(NoiseControlMode.ADAPTIVE) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.ADAPTIVE) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.ADAPTIVE) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .alpha(d3a.floatValue),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                NoiseControlButton(
                    icon = ImageBitmap.imageResource(R.drawable.noise_cancellation),
                    onClick = { onModeSelected(NoiseControlMode.NOISE_CANCELLATION) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.NOISE_CANCELLATION) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.NOISE_CANCELLATION) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 1.dp)
        ) {
            if (offListeningMode.value) {
                Text(
                    text = "Off",
                    style = TextStyle(fontSize = 12.sp, color = textColor),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                text = "Transparency",
                style = TextStyle(fontSize = 12.sp, color = textColor),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Adaptive",
                style = TextStyle(fontSize = 12.sp, color = textColor),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Noise Cancellation",
                style = TextStyle(fontSize = 12.sp, color = textColor),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Preview
@Composable
fun NoiseControlSettingsPreview() {
    NoiseControlSettings(AirPodsService())
}
