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

@file:OptIn(ExperimentalHazeMaterialsApi::class)

package me.kavishdevar.aln.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.CupertinoMaterials
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.flow.MutableStateFlow
import me.kavishdevar.aln.R
import me.kavishdevar.aln.services.ServiceManager

@RequiresApi(Build.VERSION_CODES.Q)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "UnspecifiedRegisterReceiverFlag")
@Composable
fun DebugScreen(navController: NavController) {
    val hazeState = remember { HazeState() }
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val scrollOffset by remember { derivedStateOf { listState.firstVisibleItemScrollOffset } }
    val packetLogsFlow = remember { MutableStateFlow(emptySet<String>()) }
    val expandedItems = remember { mutableStateOf(setOf<Int>()) }

    LaunchedEffect(Unit) {
        ServiceManager.getService()?.packetLogsFlow?.collect { packetLogsFlow.value = it }
    }
    val packetLogs = packetLogsFlow.collectAsState(setOf()).value

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Debug") },
                navigationIcon = {
                    TextButton(
                        onClick = {
                            navController.popBackStack()
                        },
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        val sharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Back",
                            tint = if (isSystemInDarkTheme())  Color(0xFF007AFF) else Color(0xFF3C6DF5),
                            modifier = Modifier.scale(1.5f)
                        )
                        Text(
                            sharedPreferences.getString("name", "AirPods")!!,
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isSystemInDarkTheme()) Color(0xFF007AFF) else Color(0xFF3C6DF5),
                                fontFamily = FontFamily(Font(R.font.sf_pro))
                            ),
                        )
                    }
                },
                modifier = Modifier
                    .hazeChild(
                        state = hazeState,
                        style = CupertinoMaterials.thick(),
                        block = {
                            alpha = if (scrollOffset > 0) {
                                1f
                            } else {
                                0f
                            }
                        }
                    ),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                ),
            )
        },
        containerColor = if (isSystemInDarkTheme()) Color(0xFF000000)
        else Color(0xFFF2F2F7),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
//                .imePadding()
                .haze(hazeState)
                .padding(top = paddingValues.calculateTopPadding())
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                content = {
                    items(packetLogs.size) { index ->
                        val message = packetLogs.elementAt(index)
                        val isSent = message.startsWith("Sent")
                        val isExpanded = expandedItems.value.contains(index)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp, horizontal = 4.dp)
                                .clickable {
                                    expandedItems.value = if (isExpanded) {
                                        expandedItems.value - index
                                    } else {
                                        expandedItems.value + index
                                    }
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color.Transparent
                            )
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isSent) Icons.AutoMirrored.Filled.KeyboardArrowLeft else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = null,
                                        tint = if (isSent) Color.Green else Color.Red,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column {
                                        Text(
                                            text =
                                                if (isSent) message.substring(5).take(60) + (if (message.substring(5).length > 60) "..." else "")
                                                else message.substring(9).take(60) + (if (message.substring(9).length > 60) "..." else ""),
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        if (isExpanded) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = message.substring(if (isSent) 5 else 9),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            val airPodsService = ServiceManager.getService()?.let { mutableStateOf(it) }
            HorizontalDivider()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isSystemInDarkTheme()) Color(0xFF1C1B20) else Color(0xFFF2F2F7)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val packet = remember { mutableStateOf(TextFieldValue("")) }
                TextField(
                    value = packet.value,
                    onValueChange = { packet.value = it },
                    label = { Text("Packet") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .padding(bottom = 5.dp),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                airPodsService?.value?.sendPacket(packet.value.text)
                                packet.value = TextFieldValue("")
                            }
                        ) {
                            @Suppress("DEPRECATION")
                            Icon(Icons.Filled.Send, contentDescription = "Send")
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = if (isSystemInDarkTheme()) Color(0xFF1C1B20) else Color(0xFFF2F2F7),
                        unfocusedContainerColor = if (isSystemInDarkTheme()) Color(0xFF1C1B20) else Color(0xFFF2F2F7),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor =  if (isSystemInDarkTheme()) Color.White else Color.Black,
                        unfocusedTextColor =  if (isSystemInDarkTheme()) Color.White else Color.Black.copy(alpha = 0.6f),
                        focusedLabelColor =  if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.6f) else Color.Black,
                        unfocusedLabelColor =  if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f),
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
}
