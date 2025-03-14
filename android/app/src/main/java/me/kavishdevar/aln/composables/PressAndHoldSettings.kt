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

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
    val dividerColor = Color(0x40888888)
    var leftBackgroundColor by remember { mutableStateOf(if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)) }
    var rightBackgroundColor by remember { mutableStateOf(if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)) }

    val animationSpec = tween<Color>(durationMillis = 500)
    val animatedLeftBackgroundColor by animateColorAsState(targetValue = leftBackgroundColor, animationSpec = animationSpec)
    val animatedRightBackgroundColor by animateColorAsState(targetValue = rightBackgroundColor, animationSpec = animationSpec)

    Text(
        text = stringResource(R.string.press_and_hold_airpods).uppercase(),
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            color = textColor.copy(alpha = 0.6f),
            fontFamily = FontFamily(Font(R.font.sf_pro))
        ),
        modifier = Modifier.padding(8.dp, bottom = 2.dp)
    )

    Spacer(modifier = Modifier.height(1.dp))

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF), RoundedCornerShape(14.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .background(animatedLeftBackgroundColor, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            leftBackgroundColor = dividerColor
                            tryAwaitRelease()
                            leftBackgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                        },
                        onTap = {
                            navController.navigate("long_press/Left")
                        }
                    )
                },
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
            color = dividerColor,
            modifier = Modifier
                .padding(start = 16.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .background(animatedRightBackgroundColor, RoundedCornerShape(bottomEnd = 14.dp, bottomStart = 14.dp))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            rightBackgroundColor = dividerColor
                            tryAwaitRelease()
                            rightBackgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                        },
                        onTap = {
                            navController.navigate("long_press/Right")
                        }
                    )
                },
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
