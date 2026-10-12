package com.example.quanlynongtrai

data class SensorData(
    val deviceId: String,
    val temperature: Float,
    val humidity: Float,
    val soilMoisture: Float,
    val lightLevel: Float
)

data class DeviceStatus(
    val deviceId: String,
    val pumpOn: Boolean,
    val event: String,
    val duration: Int = 0,
    val autoConfig: AutoConfig? = null
)

data class AutoConfig(
    val enabled: Boolean,
    val threshold: Float,
    val duration: Int,
    val cooldown: Int
)

// Thêm class này nếu DatabaseHelper của bạn chưa có
data class LogItem(
    val time: String,
    val type: String,
    val desc: String
)