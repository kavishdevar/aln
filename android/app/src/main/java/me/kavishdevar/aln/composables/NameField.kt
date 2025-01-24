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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController

@Composable
fun NameField(
    name: String,
    value: String,
    navController: NavController
) {
    var isFocused by remember { mutableStateOf(false) }

    val isDarkTheme = isSystemInDarkTheme()

    var backgroundColor by remember { mutableStateOf(if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)) }
    val animatedBackgroundColor by animateColorAsState(targetValue = backgroundColor, animationSpec = tween(durationMillis = 500))

    val textColor = if (isDarkTheme) Color.White else Color.Black
    val cursorColor = if (isFocused) {
        if (isDarkTheme) Color.White else Color.Black
    } else {
        Color.Transparent
    }

    Box (
        modifier = Modifier
            .background(animatedBackgroundColor, RoundedCornerShape(14.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        backgroundColor = if (isDarkTheme) Color(0x40888888) else Color(0x40D9D9D9)
                        tryAwaitRelease()
                        backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
                    },
                    onTap = {
                        navController.navigate("rename")
                    }
                )
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .background(
                    animatedBackgroundColor,
                    RoundedCornerShape(14.dp)
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)

        ) {
            Text(
                text = name,
                style = TextStyle(
                    fontSize = 16.sp,
                    color = textColor
                )
            )
            BasicTextField(
                value = value,
                textStyle = TextStyle(
                    color = textColor.copy(alpha = 0.75f),
                    fontSize = 16.sp,
                    textAlign = TextAlign.End
                ),
                onValueChange = {},
                singleLine = true,
                enabled = false,
                cursorBrush = SolidColor(cursorColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                    },
                decorationBox = { innerTextField ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        innerTextField()
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Edit name",
                            tint = textColor.copy(alpha = 0.75f),
                            modifier = Modifier
                                .size(32.dp)
                        )
                    }
                }
            )
        }
    }
}

@Preview
@Composable
fun StyledTextFieldPreview() {
    NameField(name = "Name", value = "AirPods Pro", rememberNavController())
}