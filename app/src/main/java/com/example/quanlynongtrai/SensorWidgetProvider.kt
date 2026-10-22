package com.example.quanlynongtrai

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

class SensorWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidgetUI(context, appWidgetManager, appWidgetId, "--", "--")
        }
    }

    companion object {
        fun updateWidgetUI(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            temp: String,
            soil: String
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_sensor).apply {
                setTextViewText(R.id.widgetTvTemp, "Nhiệt độ: $temp °C")
                setTextViewText(R.id.widgetTvSoil, "Độ ẩm đất: $soil %")
            }
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}