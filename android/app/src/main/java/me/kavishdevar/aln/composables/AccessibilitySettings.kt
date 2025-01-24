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

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.aln.R
import me.kavishdevar.aln.services.AirPodsService

@Composable
fun AccessibilitySettings(service: AirPodsService, sharedPreferences: SharedPreferences) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)

    Text(
        text = stringResource(R.string.accessibility).uppercase(),
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
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .padding(top = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = stringResource(R.string.tone_volume),
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 2.dp, start = 2.dp)
                    .fillMaxWidth(),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            )

            ToneVolumeSlider(service = service, sharedPreferences = sharedPreferences)
        }

        val pressSpeedOptions = listOf("Default", "Slower", "Slowest")
        var selectedPressSpeed by remember { mutableStateOf(pressSpeedOptions[0]) }
        DropdownMenuComponent(
            label = "Press Speed",
            options = pressSpeedOptions,
            selectedOption = selectedPressSpeed,
            onOptionSelected = {
                selectedPressSpeed = it
                service.setPressSpeed(pressSpeedOptions.indexOf(it))
            },
            textColor = textColor
        )

        val pressAndHoldDurationOptions = listOf("Default", "Slower", "Slowest")
        var selectedPressAndHoldDuration by remember { mutableStateOf(pressAndHoldDurationOptions[0]) }
        DropdownMenuComponent(
            label = "Press and Hold Duration",
            options = pressAndHoldDurationOptions,
            selectedOption = selectedPressAndHoldDuration,
            onOptionSelected = {
                selectedPressAndHoldDuration = it
                service.setPressAndHoldDuration(pressAndHoldDurationOptions.indexOf(it))
            },
            textColor = textColor
        )

        val volumeSwipeSpeedOptions = listOf("Default", "Longer", "Longest")
        var selectedVolumeSwipeSpeed by remember { mutableStateOf(volumeSwipeSpeedOptions[0]) }
        DropdownMenuComponent(
            label = "Volume Swipe Speed",
            options = volumeSwipeSpeedOptions,
            selectedOption = selectedVolumeSwipeSpeed,
            onOptionSelected = {
                selectedVolumeSwipeSpeed = it
                service.setVolumeSwipeSpeed(volumeSwipeSpeedOptions.indexOf(it))
            },
            textColor = textColor
        )

        SinglePodANCSwitch(service = service, sharedPreferences = sharedPreferences)
        VolumeControlSwitch(service = service, sharedPreferences = sharedPreferences)
        TransparencySettings(service = service, sharedPreferences = sharedPreferences)
    }
}

@Composable
fun DropdownMenuComponent(
    label: String,
    options: List<String>,
    selectedOption: String,
    onOptionSelected: (String) -> Unit,
    textColor: Color
) {
    var expanded by remember { mutableStateOf(false) }

    Column (
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .padding(8.dp)
        ) {
            Text(
                text = selectedOption,
                modifier = Modifier.padding(16.dp),
                color = textColor
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                    text = { Text(text = option) }
                )
            }
        }
    }
}

@Preview
@Composable
fun AccessibilitySettingsPreview() {
    AccessibilitySettings(service = AirPodsService(), sharedPreferences = LocalContext.current.getSharedPreferences("preview", Context.MODE_PRIVATE))
}
