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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import me.kavishdevar.aln.R


@Composable
fun NavigationButton(to: String, name: String, navController: NavController) {
    Row(
        modifier = Modifier
            .background(
                if (isSystemInDarkTheme()) Color(
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
            color = if (isSystemInDarkTheme()) Color.White else Color.Black
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(
            onClick = { navController.navigate(to) },
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Transparent,
                contentColor = if (isSystemInDarkTheme()) Color.White else Color.Black
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

@Preview
@Composable
fun NavigationButtonPreview() {
    NavigationButton("to", stringResource(R.string.name), NavController(LocalContext.current))
}
