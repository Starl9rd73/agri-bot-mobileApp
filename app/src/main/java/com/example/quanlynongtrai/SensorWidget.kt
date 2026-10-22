package com.example.quanlynongtrai

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class SensorWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
        val temp = prefs.getString("temp", "--") ?: "--"
        val hum = prefs.getString("hum", "--") ?: "--"
        val soil = prefs.getString("soil", "--") ?: "--"
        val light = prefs.getString("light", "--") ?: "--"

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, temp, hum, soil, light)
        }
    }

    companion object {
        fun saveAndPushUpdate(
            context: Context,
            temp: String,
            hum: String,
            soil: String,
            light: String
        ) {
            val prefs = context.getSharedPreferences("WidgetPrefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("temp", temp)
                .putString("hum", hum)
                .putString("soil", soil)
                .putString("light", light)
                .apply()

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SensorWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId, temp, hum, soil, light)
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            temp: String,
            hum: String,
            soil: String,
            light: String
        ) {
            val views = RemoteViews(context.packageName, R.layout.sensor_widget)
            views.setTextViewText(R.id.widgetTvTemp, "$temp °C")
            views.setTextViewText(R.id.widgetTvHum, "$hum %")
            views.setTextViewText(R.id.widgetTvSoil, "$soil %")
            views.setTextViewText(R.id.widgetTvLight, "$light Lux")

            // Chạm vào bất kỳ đâu trên Widget sẽ mở MainActivity
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widgetRoot, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}