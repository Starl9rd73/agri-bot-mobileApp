package com.example.quanlynongtrai

import android.util.Log
import org.eclipse.paho.client.mqttv3.*
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import java.util.Properties

class MqttManager(
    private val serverUri: String = "ssl://b12f446d03134355bd6026903779fbbb.s1.eu.hivemq.cloud:8883",
    private val clientId: String = MqttClient.generateClientId()
) {
    private lateinit var mqttClient: MqttClient

    // Callbacks để truyền dữ liệu về cho ViewModel
    var onConnectionStatusChanged: ((Boolean, String) -> Unit)? = null
    var onMessageReceived: ((String, String) -> Unit)? = null

    fun connect() {
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
                    onConnectionStatusChanged?.invoke(true, "Đã kết nối HiveMQ")
                    subscribe("sensors/+/data")
                    subscribe("sensors/+/status")
                }

                override fun connectionLost(cause: Throwable?) {
                    onConnectionStatusChanged?.invoke(false, "Mất kết nối MQTT")
                }

                override fun messageArrived(topic: String?, message: MqttMessage?) {
                    val payload = message?.toString() ?: return
                    topic?.let { onMessageReceived?.invoke(it, payload) }
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })

            mqttClient.connect(options)
        } catch (e: Exception) {
            Log.e("MqttManager", "Lỗi kết nối: ${e.message}")
            onConnectionStatusChanged?.invoke(false, "Lỗi: ${e.message}")
        }
    }

    private fun subscribe(topic: String, qos: Int = 1) {
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected) {
                mqttClient.subscribe(topic, qos)
            }
        } catch (e: Exception) {
            Log.e("MqttManager", "Lỗi subscribe: ${e.message}")
        }
    }

    fun publish(topic: String, payload: String, qos: Int = 1) {
        try {
            if (::mqttClient.isInitialized && mqttClient.isConnected) {
                val message = MqttMessage(payload.toByteArray()).apply { this.qos = qos }
                mqttClient.publish(topic, message)
            }
        } catch (e: Exception) {
            Log.e("MqttManager", "Lỗi publish: ${e.message}")
        }
    }

    fun disconnect() {
        if (::mqttClient.isInitialized && mqttClient.isConnected) {
            mqttClient.disconnect()
        }
    }
}