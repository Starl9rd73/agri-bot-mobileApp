package com.example.quanlynongtrai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

object SensorNotificationHelper {
    private const val CHANNEL_ID = "agri_live_sensor_channel"
    private const val NOTIFICATION_ID = 1001

    fun showOrUpdateNotification(context: Context, data: SensorData) {
        // Kiểm tra quyền trên Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Tạo Channel với độ ưu tiên LOW để cập nhật liên tục không kêu chuông
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Giám sát cảm biến trực tiếp",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hiển thị thông số nông trại liên tục trên thanh thông báo"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Bấm vào thông báo sẽ mở MainActivity
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val lightInt = data.lightLevel.toInt()
        val summaryLine = "️ ${data.temperature}°C  |   ${data.humidity}%  |   ${data.soilMoisture}%  |   ${lightInt} lx"
        val expandedText = "️ Nhiệt độ: ${data.temperature} °C\n" +
                " Độ ẩm không khí: ${data.humidity} %\n" +
                " Độ ẩm đất: ${data.soilMoisture} %\n" +
                "️ Ánh sáng: $lightInt Lux"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(" Nông Trại Smart (Đang hoạt động)")
            .setContentText(summaryLine)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setOngoing(true)      // Ghim cố định trên thanh thông báo
            .setOnlyAlertOnce(true) // Không rung/kêu khi cập nhật số mới
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}