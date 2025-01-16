package me.kavishdevar.aln.composables

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.aln.R
import me.kavishdevar.aln.services.AirPodsService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransparencySettings(service: AirPodsService, sharedPreferences: SharedPreferences) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black

    var transparencyModeCustomizationEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("transparency_mode_customization", false)) }
    var amplification by remember { mutableIntStateOf(sharedPreferences.getInt("transparency_amplification", 0)) }
    var balance by remember { mutableIntStateOf(sharedPreferences.getInt("transparency_balance", 0)) }
    var tone by remember { mutableIntStateOf(sharedPreferences.getInt("transparency_tone", 0)) }
    var ambientNoise by remember { mutableIntStateOf(sharedPreferences.getInt("transparency_ambient_noise", 0)) }
    var conversationBoostEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("transparency_conversation_boost", false)) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    transparencyModeCustomizationEnabled = !transparencyModeCustomizationEnabled
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 4.dp)
            ) {
                Text(
                    text = "Transparency Mode",
                    fontSize = 16.sp,
                    color = textColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "You can customize Transparency mode for your AirPods Pro.",
                    fontSize = 12.sp,
                    color = textColor.copy(0.6f),
                    lineHeight = 14.sp,
                )
            }
            StyledSwitch(
                checked = transparencyModeCustomizationEnabled,
                onCheckedChange = {
                    transparencyModeCustomizationEnabled = it
                },
            )
        }
        if (transparencyModeCustomizationEnabled) {
            Spacer(modifier = Modifier.height(8.dp))
            SliderRow(
                label = "Amplification",
                value = amplification,
                onValueChange = {
                    amplification = it
                    sharedPreferences.edit().putInt("transparency_amplification", it).apply()
                },
                isDarkTheme = isDarkTheme
            )
            Spacer(modifier = Modifier.height(8.dp))
            SliderRow(
                label = "Balance",
                value = balance,
                onValueChange = {
                    balance = it
                    sharedPreferences.edit().putInt("transparency_balance", it).apply()
                },
                isDarkTheme = isDarkTheme
            )
            Spacer(modifier = Modifier.height(8.dp))
            SliderRow(
                label = "Tone",
                value = tone,
                onValueChange = {
                    tone = it
                    sharedPreferences.edit().putInt("transparency_tone", it).apply()
                },
                isDarkTheme = isDarkTheme
            )
            Spacer(modifier = Modifier.height(8.dp))
            SliderRow(
                label = "Ambient Noise",
                value = ambientNoise,
                onValueChange = {
                    ambientNoise = it
                    sharedPreferences.edit().putInt("transparency_ambient_noise", it).apply()
                },
                isDarkTheme = isDarkTheme
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        conversationBoostEnabled = !conversationBoostEnabled
                    },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 4.dp)
                ) {
                    Text(
                        text = "Conversation Boost",
                        fontSize = 16.sp,
                        color = textColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Conversation Boost focuses your AirPods on the person in front of you, making it easier to hear in a face-to-face conversation.",
                        fontSize = 12.sp,
                        color = textColor.copy(0.6f),
                        lineHeight = 14.sp,
                    )
                }
                StyledSwitch(
                    checked = conversationBoostEnabled,
                    onCheckedChange = {
                        conversationBoostEnabled = it
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderRow(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    isDarkTheme: Boolean
) {
    val trackColor = if (isDarkTheme) Color(0xFFB3B3B3) else Color(0xFF929491)
    val activeTrackColor = if (isDarkTheme) Color(0xFF007AFF) else Color(0xFF3C6DF5)
    val thumbColor = Color(0xFFFFFFFF)
    val labelTextColor = if (isDarkTheme) Color.White else Color.Black

    Row(
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 16.sp,
                color = labelTextColor,
                fontFamily = FontFamily(Font(R.font.sf_pro))
            )
        )
        Text(
            text = "\uDBC0\uDEA1",
            style = TextStyle(
                fontSize = 16.sp,
                color = labelTextColor,
                fontFamily = FontFamily(Font(R.font.sf_pro))
            ),
            modifier = Modifier.padding(start = 4.dp)
        )
        Slider(
            value = value.toFloat(),
            onValueChange = {
                onValueChange(it.toInt())
            },
            valueRange = 0f..100f,
            onValueChangeFinished = {
                onValueChange(value)
            },
            modifier = Modifier
                .weight(1f)
                .height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = trackColor
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(trackColor, RoundedCornerShape(4.dp))
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(value.toFloat() / 100)
                            .height(4.dp)
                            .background(activeTrackColor, RoundedCornerShape(4.dp))
                    )
                }
            }
        )
        Text(
            text = "\uDBC0\uDEA9",
            style = TextStyle(
                fontSize = 16.sp,
                color = labelTextColor,
                fontFamily = FontFamily(Font(R.font.sf_pro))
            ),
            modifier = Modifier.padding(end = 4.dp)
        )
    }
}