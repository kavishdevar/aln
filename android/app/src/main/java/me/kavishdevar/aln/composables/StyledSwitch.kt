package me.kavishdevar.aln.composables

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun StyledSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()

    val thumbColor = Color.White
    val trackColor = if (checked) Color(0xFF34C759) else if (isDarkTheme) Color(0xFF5B5B5E) else Color(0xFFD1D1D6)

    // Animate the horizontal offset of the thumb
    val thumbOffsetX by animateDpAsState(targetValue = if (checked) 20.dp else 0.dp, label = "Test")

    Box(
        modifier = Modifier
            .width(51.dp)
            .height(31.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(trackColor) // Dynamic track background
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffsetX) // Animate the offset for smooth transition
                .size(27.dp)
                .clip(CircleShape)
                .background(thumbColor) // Dynamic thumb color
                .clickable { onCheckedChange(!checked) } // Make the switch clickable
        )
    }
}

@Preview
@Composable
fun StyledSwitchPreview() {
    StyledSwitch(checked = true, onCheckedChange = {})
}