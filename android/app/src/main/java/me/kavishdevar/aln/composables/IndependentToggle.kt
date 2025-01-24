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

import android.content.SharedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.aln.services.AirPodsService

@Composable
fun IndependentToggle(name: String, service: AirPodsService, functionName: String, sharedPreferences: SharedPreferences, default: Boolean = false) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black

    val snakeCasedName = name.replace(Regex("[\\W\\s]+"), "_").lowercase()
    var checked by remember { mutableStateOf(default) }
    var backgroundColor by remember { mutableStateOf(if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)) }
    val animatedBackgroundColor by animateColorAsState(targetValue = backgroundColor, animationSpec = tween(durationMillis = 500))

    LaunchedEffect(sharedPreferences) {
        checked = sharedPreferences.getBoolean(snakeCasedName, true)
    }
    Box (
        modifier = Modifier
            .padding(vertical = 8.dp)
            .background(animatedBackgroundColor, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        backgroundColor = if (isDarkTheme) Color(0x40888888) else Color(0x40D9D9D9)
                        tryAwaitRelease()
                        backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                    },
                    onTap = {
                        checked = !checked
                        sharedPreferences
                            .edit()
                            .putBoolean(snakeCasedName, checked)
                            .apply()

                        val method = service::class.java.getMethod(functionName, Boolean::class.java)
                        method.invoke(service, checked)
                    }
                )
            },
    )
    {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = name, modifier = Modifier.weight(1f), fontSize = 16.sp, color = textColor)
            StyledSwitch(
                checked = checked,
                onCheckedChange = {
                    checked = it
                    sharedPreferences.edit().putBoolean(snakeCasedName, it).apply()
                    val method = service::class.java.getMethod(functionName, Boolean::class.java)
                    method.invoke(service, it)
                },
            )
        }
    }
}

@Preview
@Composable
fun IndependentTogglePreview() {
    IndependentToggle("Test", AirPodsService(), "test", LocalContext.current.getSharedPreferences("preview", 0), true)
}