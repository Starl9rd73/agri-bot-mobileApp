package com.example.quanlynongtrai

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val mqttManager = MqttManager()
    private val gson = Gson()
    private val dbHelper = DatabaseHelper(application)
    private var currentDeviceId: String? = null

    // LiveData để View (MainActivity) observe (lắng nghe)
    val connectionStatus = MutableLiveData<String>()
    val sensorData = MutableLiveData<SensorData>()
    val deviceStatus = MutableLiveData<DeviceStatus>()
    val historyLogs = MutableLiveData<List<DatabaseHelper.ActivityLog>>() // Giả sử class log của bạn tên là LogItem

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
        // Giả sử hàm getHistory trả về List. Nếu code cũ trả về String, bạn nên sửa dbHelper trả về List để UI tự map.
        historyLogs.postValue(dbHelper.getHistory())
    }

    override fun onCleared() {
        super.onCleared()
        mqttManager.disconnect()
    }
}