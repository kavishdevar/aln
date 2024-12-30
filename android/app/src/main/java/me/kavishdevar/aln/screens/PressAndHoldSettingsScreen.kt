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
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import me.kavishdevar.aln.R
import me.kavishdevar.aln.services.ServiceManager

@Composable()
fun RightDivider() {
    HorizontalDivider(
        thickness = 1.5.dp,
        color = Color(0x40888888),
        modifier = Modifier
            .padding(start = 72.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LongPress(navController: NavController, name: String) {
    val sharedPreferences = LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val offChecked = remember { mutableStateOf(sharedPreferences.getBoolean("long_press_off", false)) }
    val ncChecked = remember { mutableStateOf(sharedPreferences.getBoolean("long_press_nc", false)) }
    val transparencyChecked = remember { mutableStateOf(sharedPreferences.getBoolean("long_press_transparency", false)) }
    val adaptiveChecked = remember { mutableStateOf(sharedPreferences.getBoolean("long_press_adaptive", false)) }
    Log.d("LongPress", "offChecked: ${offChecked.value}, ncChecked: ${ncChecked.value}, transparencyChecked: ${transparencyChecked.value}, adaptiveChecked: ${adaptiveChecked.value}")
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                        Text(
                            name,
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
                            sharedPreferences.getString("name", "AirPods")!!,
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
        val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
        Column (
          modifier = Modifier
              .fillMaxSize()
              .padding(paddingValues = paddingValues)
              .padding(horizontal = 16.dp)
              .padding(top = 8.dp)
        ) {
            Text(
                text = "NOISE CONTROL",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = textColor.copy(alpha = 0.6f),
                ),
                fontFamily = FontFamily(Font(R.font.sf_pro)),
                modifier = Modifier
                    .padding(8.dp, bottom = 4.dp)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor, RoundedCornerShape(14.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val offListeningMode = sharedPreferences.getBoolean("off_listening_mode", false)
                LongPressElement("Off", offChecked, "long_press_off", offListeningMode, R.drawable.noise_cancellation)
                if (offListeningMode) RightDivider()
                LongPressElement("Transparency", transparencyChecked, "long_press_transparency", resourceId = R.drawable.transparency)
                RightDivider()
                LongPressElement("Adaptive", adaptiveChecked, "long_press_adaptive", resourceId = R.drawable.adaptive)
                RightDivider()
                LongPressElement("Noise Cancellation", ncChecked, "long_press_nc", resourceId = R.drawable.noise_cancellation)
            }
            Text(
                "Press and hold the stem to cycle between the selected noise control modes.",
                fontSize = 16.sp,
                fontFamily = FontFamily(Font(R.font.sf_pro)),
                color = textColor.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Composable
fun LongPressElement (name: String, checked: MutableState<Boolean>, id: String, enabled: Boolean = true, resourceId: Int) {
    val sharedPreferences =
        LocalContext.current.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val offListeningMode = sharedPreferences.getBoolean("off_listening_mode", false)
    val darkMode = isSystemInDarkTheme()
    val textColor = if (darkMode) Color.White else Color.Black
    val desc = when (name) {
        "Off" -> "Turns off noise management"
        "Noise Cancellation" -> "Blocks out external sounds"
        "Transparency" -> "Lets in external sounds"
        "Adaptive" -> "Dynamically adjust external noise"
        else -> ""
    }
    fun valueChanged(value: Boolean = !checked.value) {
        val originalLongPressArray = booleanArrayOf(
            sharedPreferences.getBoolean("long_press_off", false),
            sharedPreferences.getBoolean("long_press_nc", false),
            sharedPreferences.getBoolean("long_press_transparency", false),
            sharedPreferences.getBoolean("long_press_adaptive", false)
        )
        if (!value && originalLongPressArray.count { it } <= 2) {
            return
        }
        checked.value = value
        with(sharedPreferences.edit()) {
            putBoolean(id, checked.value)
            apply()
        }
        val newLongPressArray = booleanArrayOf(
            sharedPreferences.getBoolean("long_press_off", false),
            sharedPreferences.getBoolean("long_press_nc", false),
            sharedPreferences.getBoolean("long_press_transparency", false),
            sharedPreferences.getBoolean("long_press_adaptive", false)
        )
        ServiceManager.getService()
            ?.updateLongPress(originalLongPressArray, newLongPressArray, offListeningMode)
    }
    if (!enabled) {
        valueChanged(false)
    } else {
        Row(
            modifier = Modifier
                .height(72.dp)
                .clickable(
                    onClick = { valueChanged() }
                )
                .padding(horizontal = 16.dp, vertical = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(
                bitmap = ImageBitmap.imageResource(resourceId),
                contentDescription = "Icon",
                tint = Color(0xFF007AFF),
                modifier = Modifier
                    .height(48.dp)
                    .wrapContentWidth()
            )
            Column (
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 2.dp)
                    .padding(start = 8.dp)
            )
            {
                Text(
                    name,
                    fontSize = 16.sp,
                    fontFamily = FontFamily(Font(R.font.sf_pro)),
                )
                Text (
                    desc,
                    fontSize = 14.sp,
                    color = textColor.copy(alpha = 0.6f),
                    fontFamily = FontFamily(Font(R.font.sf_pro)),
                )
            }
            Checkbox(
                checked = checked.value,
                onCheckedChange = { valueChanged() },
                colors = CheckboxDefaults.colors().copy(
                    checkedCheckmarkColor = Color(0xFF007AFF),
                    uncheckedCheckmarkColor = Color.Transparent,
                    checkedBoxColor = Color.Transparent,
                    uncheckedBoxColor = Color.Transparent,
                    checkedBorderColor = Color.Transparent,
                    uncheckedBorderColor = Color.Transparent,
                    disabledBorderColor = Color.Transparent,
                    disabledCheckedBoxColor = Color.Transparent,
                    disabledUncheckedBoxColor = Color.Transparent,
                    disabledUncheckedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .height(24.dp)
                    .scale(1.5f),
            )
        }
    }
}