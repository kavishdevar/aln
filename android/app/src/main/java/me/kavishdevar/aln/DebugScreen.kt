@file:OptIn(ExperimentalHazeMaterialsApi::class)

package me.kavishdevar.aln

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun DebugScreen(navController: NavController) {
    val hazeState = remember { HazeState() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug") },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                modifier = Modifier
                    .hazeChild(
                        state = hazeState,
                        style = CupertinoMaterials.thin()
                    ),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color(0xFF000000)
        else Color(0xFFF2F2F7),
    ) { paddingValues ->

        val text = remember { mutableStateListOf<String>("Log Start") }
        val context = LocalContext.current
        val listState = rememberLazyListState()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val data = intent.getByteArrayExtra("data")
                data?.let {
                    text.add(">" + it.joinToString(" ") { byte -> "%02X".format(byte) }) // Use ">" for received packets
                }
            }
        }

        LaunchedEffect(context) {
            val intentFilter = IntentFilter(AirPodsNotifications.AIRPODS_DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
            }
        }

        LaunchedEffect(text.size) {
            if (text.isNotEmpty()) {
                listState.animateScrollToItem(text.size - 1)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
//                .padding(paddingValues)
                .imePadding()
                .haze(hazeState)
                .padding(top = 0.dp)
        ) {
            Spacer(modifier = Modifier.height(55.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                content = {
                    items(text.size) { index ->
                        val message = text[index]
                        val isSent = message.startsWith(">")
                        val backgroundColor = if (isSent) Color(0xFFE1FFC7) else Color(0xFFD1D1D1)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .background(backgroundColor, RoundedCornerShape(12.dp))
                                .padding(12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                if (!isSent) {
                                    Text("<", color = Color(0xFF00796B), fontSize = 16.sp)
                                }

                                Text(
                                    text = if (isSent) message.substring(1) else message, // Remove the ">" from sent packets
                                    fontFamily = FontFamily(Font(R.font.hack)),
                                    color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color(0xFF000000)
                                    else Color(0xFF000000),
                                    modifier = Modifier.weight(1f) // Allows text to take available space
                                )

                                if (isSent) {
                                    Text(">", color = Color(0xFF00796B), fontSize = 16.sp)
                                }
                            }
                        }
                    }
                }
            )
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
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color(0xFF1C1B20) else Color(0xFFF2F2F7)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val packet = remember { mutableStateOf(TextFieldValue("")) }
                TextField(
                    value = packet.value,
                    onValueChange = { packet.value = it },
                    label = { Text("Packet") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp), // Padding for the input field
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                airPodsService.value?.sendPacket(packet.value.text)
                                text.add(packet.value.text) // Add sent message directly without prefix
                                packet.value = TextFieldValue("") // Clear input field after sending
                            }
                        ) {
                            @Suppress("DEPRECATION")
                            Icon(Icons.Filled.Send, contentDescription = "Send")
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color(0xFF1C1B20) else Color(0xFFF2F2F7),
                        unfocusedContainerColor = if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color(0xFF1C1B20) else Color(0xFFF2F2F7),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor =  if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color.White else Color.Black,
                        unfocusedTextColor =  if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color.White else Color.Black.copy(alpha = 0.6f),
                        focusedLabelColor =  if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color.White.copy(alpha = 0.6f) else Color.Black,
                        unfocusedLabelColor =  if (MaterialTheme.colorScheme.surface.luminance() < 0.5) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f),
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

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
            }
        }
    }
}