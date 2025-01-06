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


import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.aln.R

@Composable
fun BatteryIndicator(batteryPercentage: Int, charging: Boolean = false) {
    val batteryOutlineColor = Color(0xFFBFBFBF)
    val batteryFillColor = if (batteryPercentage > 30) Color(0xFF30D158) else Color(0xFFFC3C3C)
    val batteryTextColor = MaterialTheme.colorScheme.onSurface

    val batteryWidth = 40.dp
    val batteryHeight = 15.dp
    val batteryCornerRadius = 4.dp
    val tipWidth = 5.dp
    val tipHeight = batteryHeight * 0.375f

    val animatedFillWidth by animateFloatAsState(targetValue = batteryPercentage / 100f)
    val animatedScale by animateFloatAsState(targetValue = if (charging) 1.2f else 1f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(batteryWidth)
                    .height(batteryHeight)
            ) {
                Box (
                    modifier = Modifier
                        .fillMaxSize()
                        .border(1.dp, batteryOutlineColor, RoundedCornerShape(batteryCornerRadius))
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(2.dp)
                        .width(batteryWidth * animatedFillWidth)
                        .background(batteryFillColor, RoundedCornerShape(2.dp))
                )
                if (charging) {
                    Text(
                        text = "\uDBC0\uDEE6",
                        fontSize = 16.sp,
                        fontFamily = FontFamily(Font(R.font.sf_pro)),
                        color = Color.White,
                        modifier = Modifier
                            .scale(animatedScale)
                            .fillMaxSize(),
                        textAlign = TextAlign.Center
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(tipWidth)
                    .height(tipHeight)
                    .padding(start = 1.dp)
                    .background(
                        batteryOutlineColor,
                        RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 12.dp,
                            bottomStart = 0.dp,
                            bottomEnd = 12.dp
                        )
                    )
            )
        }

        Text(
            text = "$batteryPercentage%",
            color = batteryTextColor,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@Preview
@Composable
fun BatteryIndicatorPreview() {
    BatteryIndicator(batteryPercentage = 48, charging = true)
}