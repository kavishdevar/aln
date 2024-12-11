package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import me.kavishdevar.aln.composables.BatteryView
import me.kavishdevar.aln.composables.SinglePodANCSwitch
import me.kavishdevar.aln.composables.StyledSwitch
import me.kavishdevar.aln.composables.StyledTextField
import me.kavishdevar.aln.composables.ToneVolumeSlider
import me.kavishdevar.aln.composables.VolumeControlSwitch
import me.kavishdevar.aln.ui.theme.ALNTheme
import kotlin.math.roundToInt


@Preview(showBackground = true)
@Composable
fun BatteryViewPreview() {
    ALNTheme (darkTheme = false) {
        BatteryView(AirPodsService(), true)
    }
}

@Composable
fun AccessibilitySettings(service: AirPodsService, sharedPreferences: SharedPreferences) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
    val textColor = if (isDarkTheme) Color.White else Color.Black

    Text(
        text = "ACCESSIBILITY",
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            color = textColor.copy(alpha = 0.6f)
        ),
        modifier = Modifier.padding(8.dp, bottom = 2.dp)
    )

    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .padding(top = 2.dp)
    ) {
        
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "Tone Volume",
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 2.dp, start = 2.dp)
                    .fillMaxWidth(),
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
            )

            ToneVolumeSlider(service = service, sharedPreferences = sharedPreferences)
        }

//        Dropdown menu with 3 options, Default, Slower, Slowest – Press speed
//        Dropdown menu with 3 options, Default, Slower, Slowest – Press and hold duration
//        Dropdown menu with 3 options, Default, Slower, Slowest – Volume Swipe Speed

        SinglePodANCSwitch(service = service, sharedPreferences = sharedPreferences)
        VolumeControlSwitch(service = service, sharedPreferences = sharedPreferences)
    }
}


@Composable
fun PressAndHoldSettings(navController: NavController) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
    val textColor = if (isDarkTheme) Color.White else Color.Black

    Text(
        text = "PRESS AND HOLD AIRPODS",
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            color = textColor.copy(alpha = 0.6f)
        ),
        modifier = Modifier.padding(8.dp, bottom = 2.dp)
    )

    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .padding(top = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .background(
                    backgroundColor,
                    RoundedCornerShape(14.dp)
                )
                .clickable(
                    onClick = {
                        navController.navigate("long_press/Left")
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Left",
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = textColor
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
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
            thickness = 2.dp,
            color = Color(0xFF4D4D4D).copy(alpha = 0.4f),
            modifier = Modifier
                .padding(start = 16.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
                .background(
                    backgroundColor,
                    RoundedCornerShape(14.dp)
                )
                .clickable(
                    onClick = {
                        navController.navigate("long_press/Right")
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Right",
                    style = TextStyle(
                        fontSize = 16.sp,
                        color = textColor
                    )
                )
                Spacer(modifier = Modifier.weight(1f))
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

@Composable
fun NavigationButton(to: String, name: String, navController: NavController) {
    Row(
        modifier = Modifier
            .background(
                if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color(
                    0xFF1C1C1E
                ) else Color(0xFFFFFFFF), RoundedCornerShape(14.dp)
            )
            .height(55.dp)
            .clickable {
                navController.navigate(to)
            }
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(16.dp),
            color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color.White else Color.Black
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = { navController.navigate(to) },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color.White else Color.Black
            ),
            modifier = Modifier
                .padding(start = 16.dp)
                .fillMaxHeight()
        ) {
            @Suppress("DEPRECATION")
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = name
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
@SuppressLint("MissingPermission", "NewApi")
@Composable
fun AirPodsSettingsScreen(dev: BluetoothDevice?, service: AirPodsService,
                          navController: NavController, isConnected: Boolean) {
    val sharedPreferences = LocalContext.current.getSharedPreferences("settings", MODE_PRIVATE)
    var device by remember { mutableStateOf(dev) }
    var deviceName by remember {
        mutableStateOf(
            TextFieldValue(
                sharedPreferences.getString("name", device?.name ?: "AirPods Pro").toString()
            )
        )
    }
    val verticalScrollState  = rememberScrollState()
    val hazeState = remember { HazeState() }

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    Scaffold(
        containerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color(
            0xFF000000
        ) else Color(
            0xFFF2F2F7
        ),
        topBar = {
            val darkMode = MaterialTheme.colorScheme.surface.luminance() < 0.5
            val mDensity = remember { mutableFloatStateOf(1f) }
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            text = deviceName.text
                        )
                    },
                    modifier = Modifier
                        .hazeChild(
                            state = hazeState,
                            style = CupertinoMaterials.regular(),
                            block = {
                                alpha =
                                    if (verticalScrollState.value > 55.dp.value * mDensity.floatValue) 1f else 0f
                            }
                        )
                        .drawBehind {
                            mDensity.floatValue = density
                            val strokeWidth = 0.7.dp.value * density
                            val y = size.height - strokeWidth / 2
                            if (verticalScrollState.value > 55.dp.value * density) {
                                drawLine(
                                    if (darkMode) Color.DarkGray else Color.LightGray,
                                    Offset(0f, y),
                                    Offset(size.width, y),
                                    strokeWidth
                                )
                            }
                        },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    ),
                    actions = {
                        val context = LocalContext.current
                        IconButton(
                            onClick = {
                                ServiceManager.restartService(context)
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = Color.Transparent,
                                contentColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color.White else Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Settings",
                            )
                        }
                    }
                )
        }
    ) { paddingValues ->
        if (isConnected == true) {
            Column(
                modifier = Modifier
                    .haze(hazeState)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(
                        state = verticalScrollState,
                        enabled = true,
                    )
            ) {
                Spacer(Modifier.height(75.dp))
                LaunchedEffect(service) {
                    service.let {
                        it.sendBroadcast(Intent(AirPodsNotifications.BATTERY_DATA).apply {
                            putParcelableArrayListExtra("data", ArrayList(it.getBattery()))
                        })
                        it.sendBroadcast(Intent(AirPodsNotifications.ANC_DATA).apply {
                            putExtra("data", it.getANC())
                        })
                    }
                }
                val sharedPreferences = LocalContext.current.getSharedPreferences("settings", MODE_PRIVATE)

                Spacer(modifier = Modifier.height(64.dp))

                BatteryView(service = service)

                Spacer(modifier = Modifier.height(32.dp))

                StyledTextField(
                    name = "Name",
                    value = deviceName.text,
                    onValueChange = {
                        deviceName = TextFieldValue(it)
                        sharedPreferences.edit().putString("name", it).apply()
                        service.setName(it)
                    }
                )

                Spacer(modifier = Modifier.height(32.dp))
                NoiseControlSettings(service = service)

                Spacer(modifier = Modifier.height(16.dp))
                PressAndHoldSettings(navController = navController)

                Spacer(modifier = Modifier.height(16.dp))
                AudioSettings(service = service, sharedPreferences = sharedPreferences)

                Spacer(modifier = Modifier.height(16.dp))
                IndependentToggle(
                    name = "Automatic Ear Detection",
                    service = service,
                    functionName = "setEarDetection",
                    sharedPreferences = sharedPreferences,
                    true
                )

                Spacer(modifier = Modifier.height(16.dp))
                IndependentToggle(
                    name = "Off Listening Mode",
                    service = service,
                    functionName = "setOffListeningMode",
                    sharedPreferences = sharedPreferences,
                    false
                )

                Spacer(modifier = Modifier.height(16.dp))
                AccessibilitySettings(service = service, sharedPreferences = sharedPreferences)

                Spacer(modifier = Modifier.height(16.dp))
                NavigationButton("debug", "Debug", navController)
                Spacer(Modifier.height(24.dp))
            }
        }
        else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp)
                    .verticalScroll(
                        state = verticalScrollState,
                        enabled = true,
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "AirPods not connected",
                    style = TextStyle(
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color.White else Color.Black
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "Please connect your AirPods to access settings. If you're stuck here, then try reopening the app again after closing it from the recents.\n(DO NOT KILL THE APP!)",
                    style = TextStyle(
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color.White else Color.Black
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoiseControlSlider(service: AirPodsService, sharedPreferences: SharedPreferences) {
    val sliderValue = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(sliderValue) {
        if (sharedPreferences.contains("adaptive_strength")) {
            sliderValue.floatValue = sharedPreferences.getInt("adaptive_strength", 0).toFloat()
        }
    }
    LaunchedEffect(sliderValue.floatValue) {
        sharedPreferences.edit().putInt("adaptive_strength", sliderValue.floatValue.toInt()).apply()
    }

    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5

    val trackColor = if (isDarkTheme) Color(0xFFB3B3B3) else Color(0xFFD9D9D9)
    val thumbColor = if (isDarkTheme) Color(0xFFFFFFFF) else Color(0xFFFFFFFF)
    val labelTextColor = if (isDarkTheme) Color.White else Color.Black

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Slider(
            value = sliderValue.floatValue,
            onValueChange = {
                sliderValue.floatValue = it
                service.setAdaptiveStrength(100 - it.toInt())
            },
            valueRange = 0f..100f,
            onValueChangeFinished = {
                sliderValue.floatValue = sliderValue.floatValue.roundToInt().toFloat()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            colors = SliderDefaults.colors(
                thumbColor = thumbColor,
                inactiveTrackColor = trackColor
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .shadow(4.dp, CircleShape)
                        .background(thumbColor, CircleShape)
                )
            },
            track = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                    contentAlignment = Alignment.CenterStart
                )
                {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(trackColor, RoundedCornerShape(4.dp))
                    )
                }

            }
        )
        
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
fun IndependentToggle(name: String, service: AirPodsService, functionName: String, sharedPreferences: SharedPreferences, default: Boolean = false) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
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

    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
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

@Composable
fun PersonalizedVolumeSwitch(service: AirPodsService, sharedPreferences: SharedPreferences) {
    var personalizedVolumeEnabled by remember {
        mutableStateOf(
            sharedPreferences.getBoolean("personalized_volume", true)
        )
    }

    fun updatePersonalizedVolume(enabled: Boolean) {
        personalizedVolumeEnabled = enabled
        sharedPreferences.edit().putBoolean("personalized_volume", enabled).apply()
        service.setPVEnabled(enabled)
    }

    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
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
                updatePersonalizedVolume(!personalizedVolumeEnabled)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
        ) {
            Text(
                text = "Personalized Volume",
                fontSize = 16.sp,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Adjusts the volume of media in response to your environment.",
                fontSize = 12.sp,
                color = textColor.copy(0.6f),
                lineHeight = 14.sp,
            )
        }

        StyledSwitch(
            checked = personalizedVolumeEnabled,
            onCheckedChange = {
                updatePersonalizedVolume(it)
            },
        )
    }
}

@Composable
fun LoudSoundReductionSwitch(service: AirPodsService, sharedPreferences: SharedPreferences) {
    var loudSoundReductionEnabled by remember {
        mutableStateOf(
            sharedPreferences.getBoolean("loud_sound_reduction", true)
        )
    }

    fun updateLoudSoundReduction(enabled: Boolean) {
        loudSoundReductionEnabled = enabled
        sharedPreferences.edit().putBoolean("loud_sound_reduction", enabled).apply()
        service.setLoudSoundReduction(enabled)
    }

    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
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
                updateLoudSoundReduction(!loudSoundReductionEnabled)
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp)
        ) {
            Text(
                text = "Loud Sound Reduction",
                fontSize = 16.sp,
                color = textColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Reduces loud sounds you are exposed to.",
                fontSize = 12.sp,
                color = textColor.copy(0.6f),
                lineHeight = 14.sp,
            )
        }

        StyledSwitch(
            checked = loudSoundReductionEnabled,
            onCheckedChange = {
                updateLoudSoundReduction(it)
            },
        )
    }
}
@Composable
fun AudioSettings(service: AirPodsService, sharedPreferences: SharedPreferences) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
    val textColor = if (isDarkTheme) Color.White else Color.Black

    Text(
        text = "AUDIO",
        style = TextStyle(
            fontSize = 14.sp,
            fontWeight = FontWeight.Light,
            color = textColor.copy(alpha = 0.6f)
        ),
        modifier = Modifier.padding(8.dp, bottom = 2.dp)
    )

    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor, RoundedCornerShape(14.dp))
            .padding(top = 2.dp)
    ) {

        PersonalizedVolumeSwitch(service = service, sharedPreferences = sharedPreferences)
        ConversationalAwarenessSwitch(service = service, sharedPreferences = sharedPreferences)
        LoudSoundReductionSwitch(service = service, sharedPreferences = sharedPreferences)

        Column(
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
                    fontSize = 16.sp,
                    color = textColor
                )
            )
            Text(
                text = "Adaptive audio dynamically responds to your environment and cancels or allows external noise. You can customize Adaptive Audio to allow more or less noise.",
                modifier = Modifier
                    .padding(bottom = 8.dp, top = 2.dp)
                    .padding(end = 2.dp, start = 2.dp)
                    .fillMaxWidth(),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.6f)
                )
            )

            NoiseControlSlider(service = service, sharedPreferences = sharedPreferences)
        }
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
fun NoiseControlSettings(service: AirPodsService) {
    val isDarkTheme = MaterialTheme.colorScheme.surface.luminance() < 0.5
    val backgroundColor = if (isDarkTheme) Color(0xFF1C1C1E) else Color(0xFFE3E3E8)
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val textColorSelected = if (isDarkTheme) Color.White else Color.Black
    val selectedBackground = if (isDarkTheme) Color(0xFF5C5A5F) else Color(0xFFFFFFFF)

    val noiseControlMode = remember { mutableStateOf(NoiseControlMode.OFF) }


    val d1a = remember { mutableFloatStateOf(0f) }
    val d2a = remember { mutableFloatStateOf(0f) }
    val d3a = remember { mutableFloatStateOf(0f) }

    fun onModeSelected(mode: NoiseControlMode) {
        noiseControlMode.value = mode
        service.setANCMode(mode.ordinal+1)
        when (mode) {
            NoiseControlMode.NOISE_CANCELLATION -> {
                d1a.floatValue = 1f
                d2a.floatValue = 1f
                d3a.floatValue = 0f
            }
            NoiseControlMode.OFF -> {
                d1a.floatValue = 0f
                d2a.floatValue = 1f
                d3a.floatValue = 1f
            }
            NoiseControlMode.ADAPTIVE -> {
                d1a.floatValue = 1f
                d2a.floatValue = 0f
                d3a.floatValue = 0f
            }
            NoiseControlMode.TRANSPARENCY -> {
                d1a.floatValue = 0f
                d2a.floatValue = 0f
                d3a.floatValue = 1f
            }
        }
    }

    val noiseControlReceiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                noiseControlMode.value = NoiseControlMode.entries.toTypedArray()[intent.getIntExtra("data", 3) - 1]
                onModeSelected(noiseControlMode.value)
            }
        }
    }

    val context = LocalContext.current
    val noiseControlIntentFilter = IntentFilter(AirPodsNotifications.ANC_DATA)
    context.registerReceiver(noiseControlReceiver, noiseControlIntentFilter, Context.RECEIVER_EXPORTED)

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
                .height(75.dp)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(backgroundColor, RoundedCornerShape(14.dp))
            ) {
                NoiseControlButton(
                    icon = ImageBitmap.imageResource(R.drawable.noise_cancellation),
                    onClick = { onModeSelected(NoiseControlMode.OFF) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.OFF) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.OFF) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .alpha(d1a.floatValue),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                NoiseControlButton(
                    icon = ImageBitmap.imageResource(R.drawable.transparency),
                    onClick = { onModeSelected(NoiseControlMode.TRANSPARENCY) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.TRANSPARENCY) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.TRANSPARENCY) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .alpha(d2a.floatValue),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                NoiseControlButton(
                    icon = ImageBitmap.imageResource(R.drawable.adaptive),
                    onClick = { onModeSelected(NoiseControlMode.ADAPTIVE) },
                    textColor = if (noiseControlMode.value == NoiseControlMode.ADAPTIVE) textColorSelected else textColor,
                    backgroundColor = if (noiseControlMode.value == NoiseControlMode.ADAPTIVE) selectedBackground else Color.Transparent,
                    modifier = Modifier.weight(1f)
                )
                VerticalDivider(
                    thickness = 1.dp,
                    modifier = Modifier
                        .padding(vertical = 10.dp)
                        .alpha(d3a.floatValue),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                )
                NoiseControlButton(
                    icon = ImageBitmap.imageResource(R.drawable.noise_cancellation),
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
    icon: ImageBitmap,
    onClick: () -> Unit,
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .background(color = backgroundColor, shape = RoundedCornerShape(11.dp))
            .clickable(
                onClick = onClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            bitmap = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(40.dp)
        )
    }
}

@Preview
@Composable
fun AirPodsSettingsScreenPreview() {
    ALNTheme (
        darkTheme = true
    ) {
        AirPodsSettingsScreen(dev = null, service = AirPodsService(), navController = rememberNavController(), isConnected = true)
    }
}