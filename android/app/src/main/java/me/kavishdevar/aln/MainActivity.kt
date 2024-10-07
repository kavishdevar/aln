package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.ShapeDefaults
import androidx.compose.material3.Shapes
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kavishdevar.aln.ui.theme.ALNTheme

class MainActivity : ComponentActivity() {
    @SuppressLint("MissingPermission")
    @RequiresApi(Build.VERSION_CODES.M)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

//        val bluetoothManager: BluetoothManager = getSystemService(BluetoothManager::class.java)
//        val bluetoothAdapter = bluetoothManager.adapter
//        val device = bluetoothAdapter.getRemoteDevice("28:2d:7f:c2:05:5b")
//        val deviceName = device.name
//        val deviceAddress = device.address

        setContent {
            ALNTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AirPodsSettingsScreen(innerPadding)
                }
            }
        }
    }
}

@Composable
fun AirPodsSettingsScreen(paddingValues: PaddingValues) {
    var ancMode by remember { mutableStateOf("Off") }
    var deviceName by remember { mutableStateOf(TextFieldValue("Kavish's AirPods Pro")) }
    var conversationalAwarenessEnabled by remember { mutableStateOf(false) }
    var earDetectionEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        // Device Name Section
        Text(text = "Name", style = MaterialTheme.typography.titleMedium)
        BasicTextField(
            value = deviceName,
            onValueChange = { deviceName = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        HorizontalDivider(thickness = 2.dp, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

        // ANC Mode Section
        Text(text = "Noise Control", style = MaterialTheme.typography.titleMedium)
        ANCOptions(ancMode) { selectedMode -> ancMode = selectedMode }

        HorizontalDivider(thickness = 2.dp, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

        // Conversational Awareness Toggle
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Conversational Awareness", modifier = Modifier.weight(1f))
            Switch(
                checked = conversationalAwarenessEnabled,
                onCheckedChange = { conversationalAwarenessEnabled = it }
            )
        }

        HorizontalDivider(thickness = 2.dp, modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))

        // Ear Detection Toggle
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Automatic Ear Detection", modifier = Modifier.weight(1f))
            Switch(
                checked = earDetectionEnabled,
                onCheckedChange = { earDetectionEnabled = it }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ANCOptions(selectedMode: String, onModeChange: (String) -> Unit) {
    val modes = listOf("Off", "Transparency", "Noise Cancellation")

    SingleChoiceSegmentedButtonRow {
        modes.forEach { mode ->
            SegmentedButton(
                selected = (mode == selectedMode),
                onClick = { onModeChange(mode) },
                modifier = Modifier.weight(1f),
                shape = ShapeDefaults.ExtraSmall
            ) {
                Text(
                    text = mode,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewAirPodsSettingsScreen() {
    AirPodsSettingsScreen(PaddingValues(16.dp))
}