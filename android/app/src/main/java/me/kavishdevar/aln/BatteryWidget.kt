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


package me.kavishdevar.aln

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import me.kavishdevar.aln.services.ServiceManager
import me.kavishdevar.aln.utils.BatteryComponent
import me.kavishdevar.aln.utils.BatteryStatus

class BatteryWidget : AppWidgetProvider() {
    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        updateAppWidget(context, AppWidgetManager.getInstance(context), 0)
    }
}

internal fun updateAppWidget(
    context: Context,
    appWidgetManager: AppWidgetManager,
    appWidgetId: Int
) {
    val service = ServiceManager.getService()
    val batteryList = service?.batteryNotification?.getBattery()

    val views = RemoteViews(context.packageName, R.layout.battery_widget)

    views.setTextViewText(R.id.left_battery_widget,
        batteryList?.find { it.component == BatteryComponent.LEFT }?.let {
            if (it.status != BatteryStatus.DISCONNECTED) {
                "${if (it.status == BatteryStatus.CHARGING) "⚡" else ""} ${it.level}%"
            } else {
                ""
            }
        } ?: "")
    views.setTextViewText(R.id.right_battery_widget,
        batteryList?.find { it.component == BatteryComponent.RIGHT }?.let {
            if (it.status != BatteryStatus.DISCONNECTED) {
                "${if (it.status == BatteryStatus.CHARGING) "⚡" else ""} ${it.level}%"
            } else {
                ""
            }
        } ?: "")
    views.setTextViewText(R.id.case_battery_widget,
        batteryList?.find { it.component == BatteryComponent.CASE }?.let {
            if (it.status != BatteryStatus.DISCONNECTED) {
                "${if (it.status == BatteryStatus.CHARGING) "⚡" else ""} ${it.level}%"
            } else {
                ""
            }
        } ?: "")

    appWidgetManager.updateAppWidget(appWidgetId, views)
}