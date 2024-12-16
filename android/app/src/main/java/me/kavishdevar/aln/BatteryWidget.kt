package me.kavishdevar.aln

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.Log
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
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
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
    Log.d("BatteryWidget", "Battery list: $batteryList")

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