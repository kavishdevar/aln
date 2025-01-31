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

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import me.kavishdevar.aln.R

class DropdownItem(val name: String, val onSelect: () -> Unit) {
    fun select() {
        onSelect()
    }
}

@Composable
fun CustomDropdown(name: String, description: String = "", items: List<DropdownItem>) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
    var expanded by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(IntOffset.Zero) }
    var popupHeight by remember { mutableStateOf(0.dp) }

    val animatedHeight by animateDpAsState(
        targetValue = if (expanded) popupHeight else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (expanded) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                expanded = true
            }
            .onGloballyPositioned { coordinates ->
                val windowPosition = coordinates.localToWindow(Offset.Zero)
                offset = IntOffset(windowPosition.x.toInt(), windowPosition.y.toInt() + coordinates.size.height)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
        ) {
            Text(
                text = name,
                fontSize = 16.sp,
                color = textColor,
                maxLines = 1
            )
            if (description.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    fontSize = 12.sp,
                    color = textColor.copy(0.6f),
                    lineHeight = 14.sp,
                    maxLines = 1
                )
            }
        }
        Text(
            text = "\uDBC0\uDD8F",
            fontSize = 16.sp,
            fontFamily = FontFamily(Font(R.font.sf_pro)),
            color = textColor
        )
    }

    if (expanded) {
        Popup(
            alignment = Alignment.TopStart,
            offset = offset ,
            properties = PopupProperties(focusable = true),
            onDismissRequest = { expanded = false }
        ) {
            val density = LocalDensity.current
            Column(
                modifier = Modifier
                    .background(backgroundColor, RoundedCornerShape(8.dp))
                    .padding(8.dp)
                    .widthIn(max = 50.dp)
                    .height(animatedHeight)
                    .scale(animatedScale)
                    .onGloballyPositioned { coordinates ->
                        popupHeight = with(density) { coordinates.size.height.toDp() }
                    }
            ) {
                items.forEach { item ->
                    Text(
                        text = item.name,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                item.select()
                                expanded = false
                            }
                            .padding(8.dp),
                        color = textColor
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun CustomDropdownPreview() {
    CustomDropdown(
        name = "Volume Swipe Speed",
        items = listOf(
            DropdownItem("Always On") { },
            DropdownItem("Off") { },
            DropdownItem("Only when speaking") { }
        )
    )
}
