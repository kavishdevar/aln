package me.kavishdevar.aln.composables

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.aln.AirPodsService

@Composable
fun ConversationalAwarenessSwitch(service: AirPodsService, sharedPreferences: SharedPreferences) {
    var conversationalAwarenessEnabled by remember {
        mutableStateOf(
            sharedPreferences.getBoolean("conversational_awareness", true)
        )
    }

    fun updateConversationalAwareness(enabled: Boolean) {
        conversationalAwarenessEnabled = enabled
        sharedPreferences.edit().putBoolean("conversational_awareness", enabled).apply()
        service.setCAEnabled(enabled)
    }

    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black

    val isPressed = remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                shape = RoundedCornerShape(14.dp),
                color = if (isPressed.value) Color(0xFFE0E0E0) else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed.value = true
                        tryAwaitRelease()
                        isPressed.value = false
                    }
                )
            }
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                updateConversationalAwareness(!conversationalAwarenessEnabled)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
        ) {
            Text(
                text = "Conversational Awareness",
                fontSize = 16.sp,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Lowers media volume and reduces background noise when you start speaking to other people.",
                fontSize = 12.sp,
                color = textColor.copy(0.6f),
                lineHeight = 14.sp,
            )
        }
        StyledSwitch(
            checked = conversationalAwarenessEnabled,
            onCheckedChange = {
                updateConversationalAwareness(it)
            },
        )
    }
}

@Preview
@Composable
fun ConversationalAwarenessSwitchPreview() {
    ConversationalAwarenessSwitch(AirPodsService(), LocalContext.current.getSharedPreferences("preview", 0))
}