package com.example.quanlynongtrai

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.gson.Gson
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.Properties

class MainActivity : AppCompatActivity() {

    private lateinit var mqttClient: MqttClient
    private val gson = Gson()
    private lateinit var dbHelper: DatabaseHelper
    private var currentDeviceId: String? = null

    // UI Elements
    private lateinit var tvStatus: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvHum: TextView
    private lateinit var tvSoil: TextView
    private lateinit var tvLight: TextView
    private lateinit var tvPumpStatus: TextView
    private lateinit var switchPump: MaterialSwitch
    
    // Quick Irrigation UI
    private lateinit var etQuickDuration: EditText
    private lateinit var btnQuickIrrigate: Button
    
    // Auto Config UI
    private lateinit var switchAuto: MaterialSwitch
    private lateinit var etThreshold: EditText
    private lateinit var etDuration: EditText
    private lateinit var btnSaveAuto: Button
    
    // History UI
    private lateinit var tvHistory: TextView
    private lateinit var tvClearHistory: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbHelper = DatabaseHelper(this)
        initUI()
        setupMqtt()
        updateHistoryUI()
    }

    private fun initUI() {
        tvStatus = findViewById(R.id.tvStatus)
        tvTemp = findViewById(R.id.tvTemp)
        tvHum = findViewById(R.id.tvHum)
        tvSoil = findViewById(R.id.tvSoil)
        tvLight = findViewById(R.id.tvLight)
        tvPumpStatus = findViewById(R.id.tvPumpStatus)
        switchPump = findViewById(R.id.switchPump)
        
        etQuickDuration = findViewById(R.id.etQuickDuration)
        btnQuickIrrigate = findViewById(R.id.btnQuickIrrigate)
        
        switchAuto = findViewById(R.id.switchAuto)
        etThreshold = findViewById(R.id.etThreshold)
        etDuration = findViewById(R.id.etDuration)
        btnSaveAuto = findViewById(R.id.btnSaveAuto)
        tvHistory = findViewById(R.id.tvHistory)
        tvClearHistory = findViewById(R.id.tvClearHistory)

        switchPump.setOnCheckedChangeListener { _, isChecked ->
            val action = if (isChecked) "turn_on" else "turn_off"
            sendPumpCommand(action)
            logActivity("PUMP", "Người dùng gạt công tắc: $action")
        }

        btnQuickIrrigate.setOnClickListener {
            val duration = etQuickDuration.text.toString().toIntOrNull() ?: 10
            sendIrrigateCommand(duration)
            logActivity("PUMP", "Kích hoạt tưới nhanh: $duration giây")
            Toast.makeText(this, "Đang khởi động tưới trong $duration giây", Toast.LENGTH_SHORT).show()
        }

        btnSaveAuto.setOnClickListener {
            sendAutoConfigCommand()
        }

        tvClearHistory.setOnClickListener {
            dbHelper.clearHistory()
            updateHistoryUI()
            Toast.makeText(this, "Đã xoá lịch sử hoạt động", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateHistoryUI() {
        val history = dbHelper.getHistory()
        if (history.isEmpty()) {
            tvHistory.text = "Chưa có lịch sử..."
            return
        }
        val sb = StringBuilder()
        history.take(15).forEach { log ->
            sb.append("[${log.time}] ${log.type}: ${log.desc}\n")
        }
        tvHistory.text = sb.toString()
    }

    private fun logActivity(type: String, desc: String) {
        dbHelper.addActivity(type, desc)
        runOnUiThread { updateHistoryUI() }
    }

    private fun setupMqtt() {
        val serverUri = "ssl://b12f446d03134355bd6026903779fbbb.s1.eu.hivemq.cloud:8883"
        val clientId = MqttClient.generateClientId()
        
        try {
            mqttClient = MqttClient(serverUri, clientId, MemoryPersistence())
            val options = MqttConnectOptions().apply {
                userName = "agri_bot"
                password = "kHongbieT31".toCharArray()
                isCleanSession = true
                isAutomaticReconnect = true
                sslProperties = Properties()
            }

            mqttClient.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    runOnUiThread { tvStatus.text = "Đã kết nối HiveMQ" }
                    mqttClient.subscribe("sensors/+/data", 1)
                    mqttClient.subscribe("sensors/+/status", 1)
                }

                override fun connectionLost(cause: Throwable?) {
                    runOnUiThread { tvStatus.text = "Mất kết nối MQTT" }
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.toString() ?: return
                    if (topic != null) {
                        handleMqttMessage(topic, payload)
                    }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient.connect(options)

        } catch (e: Exception) {
            e.printStackTrace()
            runOnUiThread { tvStatus.text = "Lỗi kết nối: ${e.message}" }
        }
    }

    private fun handleMqttMessage(topic: String, payload: String) {
        try {
            if (topic.endsWith("/data")) {
                val data = gson.fromJson(payload, SensorData::class.java)
                currentDeviceId = data.deviceId
                runOnUiThread {
                    tvTemp.text = "${data.temperature} °C"
                    tvHum.text = "${data.humidity} %"
                    tvSoil.text = "${data.soilMoisture} %"
                    tvLight.text = "${data.lightLevel} Lux"
                }
            } else if (topic.endsWith("/status")) {
                val status = gson.fromJson(payload, DeviceStatus::class.java)
                currentDeviceId = status.deviceId
                runOnUiThread {
                    tvPumpStatus.text = "Trạng thái: ${if (status.pumpOn) "Đang bật" else "Tắt"}"
                    
                    // Cập nhật công tắc bơm mà không trigger listener
                    switchPump.setOnCheckedChangeListener(null)
                    switchPump.isChecked = status.pumpOn
                    switchPump.setOnCheckedChangeListener { _, isChecked ->
                        val action = if (isChecked) "turn_on" else "turn_off"
                        sendPumpCommand(action)
                        logActivity("PUMP", "Gạt công tắc: $action")
                    }

                    // Cập nhật chế độ tự động nếu có config trong payload
                    status.autoConfig?.let { cfg ->
                        switchAuto.setOnCheckedChangeListener(null)
                        switchAuto.isChecked = cfg.enabled
                        etThreshold.setText(cfg.threshold.toString())
                        etDuration.setText(cfg.duration.toString())
                        switchAuto.setOnCheckedChangeListener { isChecked, _ -> 
                             // When user toggles manual, we should probably send config too
                             // but for now let's just avoid recursion.
                        }
                    }

                    // Log sự kiện từ thiết bị
                    if (status.event != "device_online") {
                        val eventName = when(status.event) {
                            "irrigation_started" -> "Bắt đầu tưới"
                            "irrigation_completed" -> "Hoàn thành tưới"
                            "pump_on" -> "Bơm bật"
                            "pump_off" -> "Bơm tắt"
                            else -> status.event
                        }
                        logActivity("DEVICE", "$eventName ${if(status.duration > 0) "(${status.duration}s)" else ""}")
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendPumpCommand(action: String) {
        val deviceId = currentDeviceId ?: return
        val topic = "control/$deviceId/command"
        val command = mapOf(
            "action" to action,
            "component" to "pump",
            "secret" to "k2m0a2c2t270c27"
        )
        publishMessage(topic, gson.toJson(command))
    }

    private fun sendIrrigateCommand(duration: Int) {
        val deviceId = currentDeviceId ?: return
        val topic = "control/$deviceId/command"
        val command = mapOf(
            "action" to "irrigate",
            "duration" to duration,
            "secret" to "k2m0a2c2t270c27"
        )
        publishMessage(topic, gson.toJson(command))
    }

    private fun sendAutoConfigCommand() {
        val deviceId = currentDeviceId ?: return
        val topic = "control/$deviceId/command"
        
        val command = mutableMapOf<String, Any>()
        command["action"] = "set_auto_mode"
        command["secret"] = "k2m0a2c2t270c27"
        command["enabled"] = switchAuto.isChecked
        command["threshold"] = etThreshold.text.toString().toFloatOrNull() ?: 30f
        command["duration"] = etDuration.text.toString().toIntOrNull() ?: 30
        command["cooldown"] = 3600 // Mặc định 1 tiếng

        publishMessage(topic, gson.toJson(command))
        logActivity("CONFIG", "Cập nhật chế độ tự động: ${if(switchAuto.isChecked) "Bật" else "Tắt"}")
        Toast.makeText(this, "Đã gửi cấu hình tới thiết bị", Toast.LENGTH_SHORT).show()
    }

    private fun publishMessage(topic: String, payload: String) {
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.publish(topic, MqttMessage(payload.toByteArray()))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::mqttClient.isInitialized && mqttClient.isConnected) {
            mqttClient.disconnect()
        }
    }

    // Data Classes
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
}