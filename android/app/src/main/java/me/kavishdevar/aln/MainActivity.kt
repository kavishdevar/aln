package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.getSystemService
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import me.kavishdevar.aln.ui.theme.ALNTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ALNTheme {
                Scaffold (
                    containerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color(0xFF000000) else Color(0xFFFFFFFF)
                ) { innerPadding ->
                    Main(innerPadding)
                }
            }
        }
    }
}

@SuppressLint("UseOfNonLambdaOffsetOverload")
@Composable
fun StyledSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5

    val thumbColor = Color.White
    val trackColor = if (checked) Color(0xFF34C759) else if (isDarkTheme) Color(0xFF262629) else Color(0xFFD1D1D6)

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

@Composable
fun StyledTextField(
    name: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5

    val backgroundColor = if (isDarkTheme) Color(0xFF252525) else Color(0xFFFFFFFF)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val cursorColor = if (isDarkTheme) Color.White else Color.Black

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .fillMaxWidth()
            .background(
                backgroundColor,
                RoundedCornerShape(10.dp)
            ) // Dynamic background based on theme
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = name,
            style = TextStyle(
                fontSize = 16.sp,
                color = textColor // Text color based on theme
            )
        )

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                color = textColor, // Dynamic text color
                fontSize = 16.sp,
            ),
            cursorBrush = SolidColor(cursorColor), // Dynamic cursor color
            decorationBox = { innerTextField ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    innerTextField()
                }
            },
            modifier = Modifier
                .fillMaxWidth() // Ensures text field takes remaining available space
                .padding(start = 8.dp) // Padding to adjust spacing between text field and icon
        )
    }
}

@Composable
fun BatteryIndicator(batteryPercentage: Int, charging: Boolean = false) {
    val batteryOutlineColor = Color(0xFFBFBFBF) // Light gray outline
    val batteryFillColor = if (batteryPercentage > 30) Color(0xFF30D158) else Color(0xFFFC3C3C)
    val batteryTextColor = MaterialTheme.colorScheme.onSurface

    // Battery indicator dimensions
    val batteryWidth = 30.dp
    val batteryHeight = 15.dp
    val batteryCornerRadius = 4.dp
    val tipWidth = 5.dp
    val tipHeight = batteryHeight * 0.3f

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Row for battery icon and tip
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.padding(bottom = 4.dp) // Padding between icon and percentage text
        ) {
            // Battery Icon
            Box(
                modifier = Modifier
                    .width(batteryWidth)
                    .height(batteryHeight)
                    .border(1.dp, batteryOutlineColor, RoundedCornerShape(batteryCornerRadius))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(2.dp)
                        .width(batteryWidth * (batteryPercentage / 100f))
                        .background(batteryFillColor, RoundedCornerShape(2.dp))
                )
                if (charging) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(), // Take up the entire size of the outer Box
                        contentAlignment = Alignment.Center // Center the charging bolt within the Box
                    ) {
                        Text(
                            text = "\uDBC0\uDEE6",
                            fontSize = 12.sp,
                            fontFamily = FontFamily(Font(R.font.sf_pro)),
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }

            // Battery Tip (Protrusion)
            Box(
                modifier = Modifier
                    .width(tipWidth)
                    .height(tipHeight)
                    .padding(start = 1.dp)
                    .background(
                        batteryOutlineColor,
                        RoundedCornerShape(
                            topStart = 0.dp,
                            topEnd = 12.dp,
                            bottomStart = 0.dp,
                            bottomEnd = 12.dp
                        )
                    )
            )
        }

        // Battery Percentage Text
        Text(
            text = "$batteryPercentage%",
            color = batteryTextColor,
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
        )
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun Main(paddingValues: PaddingValues) {
    val bluetoothConnectPermissionState = rememberPermissionState(
        permission = "android.permission.BLUETOOTH_CONNECT"
    )

    if (bluetoothConnectPermissionState.status.isGranted) {
        val context = LocalContext.current
        val uuid: ParcelUuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")
        val bluetoothManager = getSystemService(context, BluetoothManager::class.java)
        val bluetoothAdapter = bluetoothManager?.adapter
        val devices = bluetoothAdapter?.bondedDevices
        val airpodsDevice = remember { mutableStateOf<BluetoothDevice?>(null) }
        if (devices != null) {
            for (device in devices) {
                if (device.uuids.contains(uuid)) {
                    bluetoothAdapter.getProfileProxy(context, object : BluetoothProfile.ServiceListener {
                        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                            if (profile == BluetoothProfile.A2DP) {
                                val connectedDevices = proxy.connectedDevices
                                if (connectedDevices.isNotEmpty()) {
                                    airpodsDevice.value = device
                                    if (context.getSystemService(AirPodsService::class.java) == null || context.getSystemService(AirPodsService::class.java)?.isRunning != true) {
                                        context.startService(Intent(context, AirPodsService::class.java).apply {
                                            putExtra("device", device)
                                        })
                                    }
                                }
                            }
                            bluetoothAdapter.closeProfileProxy(profile, proxy)
                        }

                        override fun onServiceDisconnected(profile: Int) { }
                    }, BluetoothProfile.A2DP)
                }
            }
        }

        val airPodsService = remember { mutableStateOf<AirPodsService?>(null) }

        val serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                val binder = service as AirPodsService.LocalBinder
                airPodsService.value = binder.getService()
                Log.d("AirPodsService", "Service connected")
            }

            override fun onServiceDisconnected(name: ComponentName) {
                airPodsService.value = null
            }
        }
        val intent = Intent(context, AirPodsService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        if (airpodsDevice.value != null)
        {
            AirPodsSettingsScreen(
                paddingValues,
                airpodsDevice.value,
                service = airPodsService.value
            )
        }
        else {
            Text("No AirPods connected")
        }
        return
    } else {
        // Permission is not granted, request it
        Column (
            modifier = Modifier.padding(24.dp),
        ){
            val textToShow = if (bluetoothConnectPermissionState.status.shouldShowRationale) {
                // If the user has denied the permission but not permanently, explain why it's needed.
                "The BLUETOOTH_CONNECT permission is important for this app. Please grant it to proceed."
            } else {
                // If the user has permanently denied the permission, inform them to enable it in settings.
                "BLUETOOTH_CONNECT permission required for this feature. Please enable it in settings."
            }
            Text(textToShow)
            Button(onClick = { bluetoothConnectPermissionState.launchPermissionRequest() }) {
                Text("Request permission")
            }
        }
    }
}

@Composable
fun BatteryView() {
    val batteryStatus = remember { mutableStateOf<List<Battery>>(listOf()) }

    @Suppress("DEPRECATION") val batteryReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                batteryStatus.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { intent.getParcelableArrayListExtra("data", Battery::class.java) } else { intent.getParcelableArrayListExtra("data") }?.toList() ?: listOf()
            }
        }
    }
    val context = LocalContext.current

    LaunchedEffect(context) {
        val batteryIntentFilter = IntentFilter(Notifications.BATTERY_DATA)
        context.registerReceiver(batteryReceiver, batteryIntentFilter, Context.RECEIVER_EXPORTED)
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
                    .scale(0.50f)
            )
            val left = batteryStatus.value.find { it.component == BatteryComponent.LEFT }
            val right = batteryStatus.value.find { it.component == BatteryComponent.RIGHT }
            if ((right?.status == BatteryStatus.CHARGING && left?.status == BatteryStatus.CHARGING) || (left?.status == BatteryStatus.NOT_CHARGING && right?.status == BatteryStatus.NOT_CHARGING))
            {
                BatteryIndicator(right.level.let { left.level.coerceAtMost(it) }, left.status == BatteryStatus.CHARGING)
            }
            else {
                Row {
                    Text(text = "\uDBC6\uDCE5", fontFamily = FontFamily(Font(R.font.sf_pro)))
                    BatteryIndicator(left?.level ?: 0, left?.status == BatteryStatus.CHARGING)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = "\uDBC6\uDCE8", fontFamily = FontFamily(Font(R.font.sf_pro)))
                    BatteryIndicator(right?.level ?: 0, right?.status == BatteryStatus.CHARGING)
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
            )
            BatteryIndicator(case?.level ?: 0)

        }
    }
}

@SuppressLint("MissingPermission", "NewApi")
@Composable
fun AirPodsSettingsScreen(paddingValues: PaddingValues, device: BluetoothDevice?, service: AirPodsService?) {
    var deviceName by remember { mutableStateOf(TextFieldValue(device?.name ?: "AirPods Pro (fallback, should never show up)")) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(vertical = 24.dp, horizontal = 12.dp)
    ) {
        BatteryView()
        if (service != null) {
            StyledTextField(
                name = "Name",
                value = deviceName.text,
                onValueChange = { deviceName = TextFieldValue(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            NoiseControlSettings(service = service)

            Spacer(modifier = Modifier.height(16.dp))
            AudioSettings(service = service)
        }
    }
}

@Composable
fun NoiseControlSlider(service: AirPodsService) {
    val sliderValue = remember { mutableStateOf(0f) }
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5

    val trackColor = if (isDarkTheme) Color(0xFFB3B3B3) else Color(0xFFD9D9D9)
    val activeTrackColor = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFF007AFF)
    val thumbColor = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFF007AFF)
    val labelTextColor = if (isDarkTheme) Color.White else Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Slider
        Slider(
            value = sliderValue.value,
            onValueChange = {
                sliderValue.value = it
                service.setAdaptiveStrength(it.toInt())
            },
            valueRange = 0f..100f,
            steps = 3,
            modifier = Modifier
                .fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                activeTrackColor = activeTrackColor,
                inactiveTrackColor = trackColor
            )
        )

        // Labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Less Noise",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = labelTextColor
                ),
                modifier = Modifier.padding(start = 4.dp)
            )
            Text(
                text = "More Noise",
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    color = labelTextColor
                ),
                modifier = Modifier.padding(end = 4.dp)
            )
        }
    }
}

@Composable
fun AudioSettings(service: AirPodsService) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
    val textColor = if (isDarkTheme) Color.White else Color.Black
    var conversationalAwarenessEnabled by remember { mutableStateOf(true) }

    Text(
        text = "AUDIO",
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            color = textColor.copy(alpha = 0.6f)
        ),
        modifier = Modifier.padding(8.dp, bottom = 2.dp)
    )
    val backgroundColor = if (isDarkTheme) Color(0xFF252525) else Color(0xFFFFFFFF)
    val isPressed = remember { mutableStateOf(false) }
    Column (
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(12.dp))
            .padding(top = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPressed.value) Color(0xFFE0E0E0) else Color.Transparent
                )
                .padding(horizontal = 12.dp, vertical = 12.dp)
                .pointerInput(Unit) { // Detect press state for iOS-like effect
                    detectTapGestures(
                        onPress = {
                            isPressed.value = true
                            tryAwaitRelease() // Wait until release
                            isPressed.value = false
                        }
                    )
                }
                .clickable(
                    indication = null, // Disable ripple effect
                    interactionSource = remember { MutableInteractionSource() } // Required for clickable
                ) {
                    conversationalAwarenessEnabled = !conversationalAwarenessEnabled
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Conversational Awareness", modifier = Modifier.weight(1f), fontSize = 16.sp, color = textColor)

            StyledSwitch(
                checked = conversationalAwarenessEnabled,
                onCheckedChange = {
                    conversationalAwarenessEnabled = it
                    service.setCAEnabled(it)
                    },
            )
        }
        Column (
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Text(
                text = "Adaptive Audio",
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 2.dp, start = 2.dp)
                    .fillMaxWidth(),
                style = TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            )
            Text(
                text = "Adaptive audio dynamically responds to your environment and cancels or allows external noise. You can customize Adaptive Audio to allow more or less noise",
                modifier = Modifier
                    .padding(8.dp, top = 2.dp)
                    .fillMaxWidth(),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.6f)
                )
            )
            NoiseControlSlider(service = service)
        }
    }
}

@Composable
fun NoiseControlSettings(service: AirPodsService) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFF2F2F7)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val textColorSelected = if (isDarkTheme) Color.White else Color.Black
    val selectedBackground = if (isDarkTheme) Color(0xFF090909) else Color(0xFFFFFFFF)

    val noiseControlMode = remember { mutableStateOf(NoiseControlMode.OFF) }

    val noiseControlReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                noiseControlMode.value = NoiseControlMode.entries.toTypedArray()[intent.getIntExtra("data", 3) - 1]
            }
        }
    }
    val context = LocalContext.current
    LaunchedEffect(context) {
        val noiseControlIntentFilter = IntentFilter(Notifications.ANC_DATA)
        context.registerReceiver(noiseControlReceiver, noiseControlIntentFilter, Context.RECEIVER_EXPORTED)
    }

//    val paddingAnim by animateDpAsState(
//        targetValue = when (noiseControlMode.value) {
//            NoiseControlMode.OFF -> 0.dp
//            NoiseControlMode.TRANSPARENCY -> 150.dp
//            NoiseControlMode.ADAPTIVE -> 250.dp
//            NoiseControlMode.NOISE_CANCELLATION -> 350.dp
//        }, label = ""
//    )

    val d1a = remember { mutableStateOf(0f) }
    val d2a = remember { mutableStateOf(0f) }
    val d3a = remember { mutableStateOf(0f) }

    fun onModeSelected(mode: NoiseControlMode) {
        noiseControlMode.value = mode
        service.setANCMode(mode.ordinal+1)
        when (mode) {
            NoiseControlMode.NOISE_CANCELLATION -> {
                d1a.value = 1f
                d2a.value = 1f
                d3a.value = 0f
            }
            NoiseControlMode.OFF -> {
                d1a.value = 0f
                d2a.value = 1f
                d3a.value = 1f
            }
            NoiseControlMode.ADAPTIVE -> {
                d1a.value = 1f
                d2a.value = 0f
                d3a.value = 0f
            }
            NoiseControlMode.TRANSPARENCY -> {
                d1a.value = 0f
                d2a.value = 0f
                d3a.value = 1f
            }
        }
    }

    Text(
        text = "NOISE CONTROL",
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            color = textColor.copy(alpha = 0.6f)
        ),
        modifier = Modifier.padding(8.dp, bottom = 2.dp)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(65.dp)
                .padding(8.dp)
        ) {
//            Box(
//                modifier = Modifier
//                    .fillMaxHeight()
//                    .width(80.dp)
//                    .offset(x = paddingAnim)
//                    .background(selectedBackground, RoundedCornerShape(8.dp))
//            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor, RoundedCornerShape(8.dp))
            ) {
                NoiseControlButton(
                    icon = Icons.Default.Person, // Replace with your icon
                    onClick = { onModeSelected(NoiseControlMode.OFF) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.OFF) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.OFF) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .alpha(d1a.value),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                NoiseControlButton(
                    icon = Icons.Default.Person, // Replace with your icon
                    onClick = { onModeSelected(NoiseControlMode.TRANSPARENCY) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.TRANSPARENCY) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.TRANSPARENCY) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .alpha(d2a.value),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                NoiseControlButton(
                    icon = Icons.Default.Person, // Replace with your icon
                    onClick = { onModeSelected(NoiseControlMode.ADAPTIVE) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.ADAPTIVE) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.ADAPTIVE) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .alpha(d3a.value),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                NoiseControlButton(
                    icon = Icons.Default.Person, // Replace with your icon
                    onClick = { onModeSelected(NoiseControlMode.NOISE_CANCELLATION) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.NOISE_CANCELLATION) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.NOISE_CANCELLATION) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(top = 1.dp)
        ) {
            Text(
                text = "Off",
                style = TextStyle(fontSize = 12.sp, color = textColor),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Transparency",
                style = TextStyle(fontSize = 12.sp, color = textColor),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Adaptive",
                style = TextStyle(fontSize = 12.sp, color = textColor),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Noise Cancellation",
                style = TextStyle(fontSize = 12.sp, color = textColor),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun NoiseControlButton(
    icon: ImageVector,
    onClick: () -> Unit,
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .background(color = backgroundColor, shape = RoundedCornerShape(6.dp))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(32.dp)
        )
    }
}

enum class NoiseControlMode {
    OFF,  NOISE_CANCELLATION, TRANSPARENCY, ADAPTIVE
}

@Preview
@Composable
fun PreviewAirPodsSettingsScreen() {
    BatteryIndicator(100, true)
}
