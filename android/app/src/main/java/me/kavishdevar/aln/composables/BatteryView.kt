package me.kavishdevar.aln.composables

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.kavishdevar.aln.utils.AirPodsNotifications
import me.kavishdevar.aln.services.AirPodsService
import me.kavishdevar.aln.utils.Battery
import me.kavishdevar.aln.utils.BatteryComponent
import me.kavishdevar.aln.utils.BatteryStatus
import me.kavishdevar.aln.R

@Composable
fun BatteryView(service: AirPodsService, preview: Boolean = false) {
    val batteryStatus = remember { mutableStateOf<List<Battery>>(listOf()) }
    @Suppress("DEPRECATION") val batteryReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == AirPodsNotifications.BATTERY_DATA) {
                    batteryStatus.value =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableArrayListExtra("data", Battery::class.java)
                        } else {
                            intent.getParcelableArrayListExtra("data")
                        }?.toList() ?: listOf()
                }
                else if (intent.action == AirPodsNotifications.DISCONNECT_RECEIVERS) {
                    context.unregisterReceiver(this)
                }
            }
        }
    }
    val context = LocalContext.current

    LaunchedEffect(context) {
        val batteryIntentFilter = IntentFilter()
            .apply {
                addAction(AirPodsNotifications.BATTERY_DATA)
                addAction(AirPodsNotifications.DISCONNECT_RECEIVERS)
            }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(
                batteryReceiver,
                batteryIntentFilter,
                Context.RECEIVER_EXPORTED
            )
        }
    }

    batteryStatus.value = service.getBattery()

    if (preview) {
        batteryStatus.value = listOf<Battery>(
            Battery(BatteryComponent.LEFT, 100, BatteryStatus.CHARGING),
            Battery(BatteryComponent.RIGHT, 50, BatteryStatus.NOT_CHARGING),
            Battery(BatteryComponent.CASE, 5, BatteryStatus.CHARGING)
        )
    }

    Row {
        Column (
            modifier = Modifier
                .fillMaxWidth(0.5f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image (
                bitmap = ImageBitmap.imageResource(R.drawable.pro_2_buds),
                contentDescription = "Buds",
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(0.80f)
            )
            val left = batteryStatus.value.find { it.component == BatteryComponent.LEFT }
            val right = batteryStatus.value.find { it.component == BatteryComponent.RIGHT }
            if ((right?.status == BatteryStatus.CHARGING && left?.status == BatteryStatus.CHARGING) || (left?.status == BatteryStatus.NOT_CHARGING && right?.status == BatteryStatus.NOT_CHARGING))
            {
                BatteryIndicator(right.level.let { left.level.coerceAtMost(it) }, left.status == BatteryStatus.CHARGING)
            }
            else {
                Row (
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    if (left?.status != BatteryStatus.DISCONNECTED) {
                        BatteryIndicator(
                            left?.level ?: 0,
                            left?.status == BatteryStatus.CHARGING
                        )
                    }
                    if (left?.status != BatteryStatus.DISCONNECTED && right?.status != BatteryStatus.DISCONNECTED) {
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    if (right?.status != BatteryStatus.DISCONNECTED) {
                        BatteryIndicator(
                            right?.level ?: 0,
                            right?.status == BatteryStatus.CHARGING
                        )
                    }
                }
            }
        }

        Column (
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val case = batteryStatus.value.find { it.component == BatteryComponent.CASE }

            Image(
                bitmap = ImageBitmap.imageResource(R.drawable.pro_2_case),
                contentDescription = "Case",
                modifier = Modifier
                    .fillMaxWidth()
                    .scale(1.25f)
            )
            if (case?.status != BatteryStatus.DISCONNECTED) {
                BatteryIndicator(case?.level ?: 0, case?.status == BatteryStatus.CHARGING)
            }
        }
    }
}

@Preview
@Composable
fun BatteryViewPreview() {
    BatteryView(AirPodsService(), preview = true)
}