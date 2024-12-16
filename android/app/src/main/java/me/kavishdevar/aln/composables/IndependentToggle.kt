package me.kavishdevar.aln.composables

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

    LaunchedEffect(sharedPreferences) {
        checked = sharedPreferences.getBoolean(snakeCasedName, true)
    }
    Box (
        modifier = Modifier
            .padding(vertical = 8.dp)
            .background(
                if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF),
                RoundedCornerShape(14.dp)
            )
            .clickable {
                checked = !checked
                sharedPreferences
                    .edit()
                    .putBoolean(snakeCasedName, checked)
                    .apply()

                val method = service::class.java.getMethod(functionName, Boolean::class.java)
                method.invoke(service, checked)
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