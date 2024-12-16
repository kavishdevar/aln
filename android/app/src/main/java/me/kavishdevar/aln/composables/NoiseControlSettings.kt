package me.kavishdevar.aln.composables

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import me.kavishdevar.aln.utils.AirPodsNotifications
import me.kavishdevar.aln.services.AirPodsService
import me.kavishdevar.aln.utils.NoiseControlMode
import me.kavishdevar.aln.R

@SuppressLint("UnspecifiedRegisterReceiverFlag")
@Composable
fun NoiseControlSettings(service: AirPodsService) {
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
        noiseControlMode.value = mode
        if (!received) service.setANCMode(mode.ordinal+1)
        when (mode) {
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
                }
                else if (intent.action == AirPodsNotifications.DISCONNECT_RECEIVERS) {
                    context.unregisterReceiver(this)
                }
            }
        }
    }

    val context = LocalContext.current
    val noiseControlIntentFilter = IntentFilter()
        .apply {
            addAction(AirPodsNotifications.ANC_DATA)
            addAction(AirPodsNotifications.DISCONNECT_RECEIVERS)
        }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.registerReceiver(noiseControlReceiver, noiseControlIntentFilter, Context.RECEIVER_EXPORTED)
    }
    else {
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
            Text(
                text = "Off",
                style = TextStyle(fontSize = 12.sp, color = textColor),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
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