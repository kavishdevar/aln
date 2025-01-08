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

package me.kavishdevar.aln.composables

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.kavishdevar.aln.R
import me.kavishdevar.aln.services.AirPodsService

@Composable
fun AudioSettings(service: AirPodsService, sharedPreferences: SharedPreferences) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black

    Text(
        text = stringResource(R.string.audio).uppercase(),
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
                text = stringResource(R.string.adaptive_audio),
                modifier = Modifier
                    .padding(end = 8.dp, bottom = 2.dp, start = 2.dp)
                    .fillMaxWidth(),
                style = TextStyle(
                    fontSize = 16.sp,
                    color = textColor
                )
            )
            Text(
                text = stringResource(R.string.adaptive_audio_description),
                modifier = Modifier
                    .padding(bottom = 8.dp, top = 2.dp)
                    .padding(end = 2.dp, start = 2.dp)
                    .fillMaxWidth(),
                style = TextStyle(
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.6f)
                )
            )

            AdaptiveStrengthSlider(service = service, sharedPreferences = sharedPreferences)
        }
    }
}

@Preview
@Composable
fun AudioSettingsPreview() {
    AudioSettings(service = AirPodsService(), sharedPreferences = LocalContext.current.getSharedPreferences("preview", Context.MODE_PRIVATE))
}
