package com.example.quanlynongtrai

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val mqttManager = MqttManager()
    private val gson = Gson()
    private val dbHelper = DatabaseHelper(application)
    private var currentDeviceId: String? = null

    // LiveData để View (Fragments) observe (lắng nghe)
    val connectionStatus = MutableLiveData<String>()
    val sensorData = MutableLiveData<SensorData>()
    val deviceStatus = MutableLiveData<DeviceStatus>()
    val historyLogs = MutableLiveData<List<DatabaseHelper.ActivityLog>>()

    init {
        setupMqttListeners()
        mqttManager.connect()
        refreshHistory()
    }

    private fun setupMqttListeners() {
        mqttManager.onConnectionStatusChanged = { isConnected, msg ->
            connectionStatus.postValue(msg)
        }

        mqttManager.onMessageReceived = { topic, payload ->
            try {
                if (topic.endsWith("/data")) {
                    val data = gson.fromJson(payload, SensorData::class.java)
                    currentDeviceId = data.deviceId
                    sensorData.postValue(data)

                    // Đẩy dữ liệu mới nhận được ra Widget ngoài màn hình chính
                    updateHomeScreenWidget(data)

                } else if (topic.endsWith("/status")) {
                    val status = gson.fromJson(payload, DeviceStatus::class.java)
                    currentDeviceId = status.deviceId
                    deviceStatus.postValue(status)

                    // Xử lý ghi log tự động
                    if (status.event != "device_online") {
                        val eventName = when(status.event) {
                            "irrigation_started" -> "Bắt đầu tưới"
                            "irrigation_completed" -> "Hoàn thành tưới"
                            "pump_on" -> "Bơm bật"
                            "pump_off" -> "Bơm tắt"
                            else -> status.event
                        }
                        val desc = "$eventName ${if(status.duration > 0) "(${status.duration}s)" else ""}"
                        addLogAndRefresh("DEVICE", desc)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Hàm chuyên trách việc cập nhật Widget
    private fun updateHomeScreenWidget(data: SensorData) {
        val context = getApplication<Application>().applicationContext
        // 1. Cập nhật Widget ngoài màn hình chính
        SensorWidget.saveAndPushUpdate(
            context,
            data.temperature.toString(),
            data.humidity.toString(),
            data.soilMoisture.toString(),
            data.lightLevel.toInt().toString()
        )
        // 2. Cập nhật Thanh thông báo liên tục (Ongoing Notification)
        SensorNotificationHelper.showOrUpdateNotification(context, data)
    }

    fun sendPumpCommand(action: String) {
        val deviceId = currentDeviceId ?: return
        val topic = "control/$deviceId/command"
        val command = mapOf("action" to action, "component" to "pump", "secret" to "k2m0a2c2t270c27")
        mqttManager.publish(topic, gson.toJson(command))
        addLogAndRefresh("PUMP", "Người dùng ra lệnh: $action")
    }

    fun sendIrrigateCommand(duration: Int) {
        val deviceId = currentDeviceId ?: return
        val topic = "control/$deviceId/command"
        val command = mapOf("action" to "irrigate", "duration" to duration, "secret" to "k2m0a2c2t270c27")
        mqttManager.publish(topic, gson.toJson(command))
        addLogAndRefresh("PUMP", "Kích hoạt tưới nhanh: $duration giây")
    }

    fun sendAutoConfigCommand(enabled: Boolean, threshold: Float, duration: Int) {
        val deviceId = currentDeviceId ?: return
        val topic = "control/$deviceId/command"
        val command = mapOf(
            "action" to "set_auto_mode",
            "secret" to "k2m0a2c2t270c27",
            "enabled" to enabled,
            "threshold" to threshold,
            "duration" to duration,
            "cooldown" to 10
        )
        mqttManager.publish(topic, gson.toJson(command))
        addLogAndRefresh("CONFIG", "Cập nhật chế độ tự động: ${if(enabled) "Bật" else "Tắt"}")
    }

    fun clearHistory() {
        dbHelper.clearHistory()
        refreshHistory()
    }

    private fun addLogAndRefresh(type: String, desc: String) {
        dbHelper.addActivity(type, desc)
        refreshHistory()
    }

    private fun refreshHistory() {
        historyLogs.postValue(dbHelper.getHistory())
    }

    override fun onCleared() {
        super.onCleared()
        mqttManager.disconnect()
    }
}