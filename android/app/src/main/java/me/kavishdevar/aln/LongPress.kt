package me.kavishdevar.aln

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavController

@Composable
fun LongPress(navController: NavController) {
    val offChecked = remember { mutableStateOf(false) }
    val ncChecked = remember { mutableStateOf(false) }
    val transparencyChecked = remember { mutableStateOf(false) }
    val adaptiveChecked = remember { mutableStateOf(false) }
    Column {
        Row {
            Text("Off")
            Checkbox(
                checked = offChecked.value,
                onCheckedChange = { offChecked.value = it },
            )
        }
        Row {
            Text("Noise Cancellation")
            Checkbox(
                checked = ncChecked.value,
                onCheckedChange = { ncChecked.value = it },
            )
        }
        Row {
            Text("Transparency")
            Checkbox(
                checked = transparencyChecked.value,
                onCheckedChange = { transparencyChecked.value = it },
            )
        }
        Row {
            Text("Off")
            Checkbox(
                checked = adaptiveChecked.value,
                onCheckedChange = { adaptiveChecked.value = it },
            )
        }
    }
}