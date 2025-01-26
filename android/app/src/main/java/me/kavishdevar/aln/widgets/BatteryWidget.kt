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


package me.kavishdevar.aln.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.util.Log
import android.widget.RemoteViews
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.core.graphics.createBitmap
import me.kavishdevar.aln.MainActivity
import me.kavishdevar.aln.R
import me.kavishdevar.aln.services.ServiceManager

class BatteryWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        updateAppWidget(context, AppWidgetManager.getInstance(context), 0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val service = ServiceManager.getService()
    val views = RemoteViews(context.packageName, R.layout.battery_widget)

    service?.updateBatteryWidget()

    val openActivityIntent = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    views.setOnClickPendingIntent(R.id.battery_widget, openActivityIntent)

    appWidgetManager.updateAppWidget(appWidgetId, views)
}