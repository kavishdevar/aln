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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

@Composable
fun PressAndHoldSettings(navController: NavController) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black

    Text(
        text = stringResource(R.string.press_and_hold_airpods),
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            color = textColor.copy(alpha = 0.6f),
            fontFamily = FontFamily(Font(R.font.sf_pro))
        ),
        modifier = Modifier.padding(8.dp, bottom = 2.dp)
    )

    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .padding(top = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .background(
                    backgroundColor,
                    RoundedCornerShape(14.dp)
                )
                .clickable(
                    onClick = {
                        navController.navigate("long_press/Left")
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.left),
                    style = TextStyle(
                        fontSize = 18.sp,
                        color = textColor,
                        fontFamily = FontFamily(Font(R.font.sf_pro))
                    ),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    // TODO: Implement voice assistant on long press; for now, it's noise control
                    text = stringResource(R.string.noise_control),
                    style = TextStyle(
                        fontSize = 18.sp,
                        color = textColor.copy(alpha = 0.6f),
                        fontFamily = FontFamily(Font(R.font.sf_pro))
                    ),
                )
                IconButton(
                    onClick = {
                        navController.navigate("long_press/Left")
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "go",
                        tint = textColor
                    )
                }
            }
        }
        HorizontalDivider(
            thickness = 1.5.dp,
            color = Color(0x40888888),
            modifier = Modifier
                .padding(start = 16.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .background(
                    backgroundColor,
                    RoundedCornerShape(18.dp)
                )
                .clickable(
                    onClick = {
                        navController.navigate("long_press/Right")
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.right),
                    style = TextStyle(
                        fontSize = 18.sp,
                        color = textColor,
                        fontFamily = FontFamily(Font(R.font.sf_pro))
                    ),
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    // TODO: Implement voice assistant on long press; for now, it's noise control
                    text = stringResource(R.string.noise_control),
                    style = TextStyle(
                        fontSize = 18.sp,
                        color = textColor.copy(alpha = 0.6f),
                        fontFamily = FontFamily(Font(R.font.sf_pro))
                    ),
                )
                IconButton(
                    onClick = {
                        navController.navigate("long_press/Right")
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "go",
                        tint = textColor
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun PressAndHoldSettingsPreview() {
    PressAndHoldSettings(navController = NavController(LocalContext.current))
}
