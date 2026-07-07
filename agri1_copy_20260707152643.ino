#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <DHT.h>
#include <EEPROM.h>
#include "soc/soc.h"
#include "soc/rtc_cntl_reg.h"

// 🔧 FreeRTOS Includes
#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <freertos/semphr.h>

// ================= DEVICE CONFIG =================
String DEVICE_ID = ""; 
#define MQTT_SECRET   "k2m0a2c2t270c27"

// ================= WIFI CONFIG =================
const char* ssid = "Phong Xe";
const char* wifi_password = "12345679";

// ================= HIVEMQ CLOUD CONFIG =================
const char* mqtt_server = "b12f446d03134355bd6026903779fbbb.s1.eu.hivemq.cloud";
const int mqtt_port = 8883;
const char* mqtt_user = "agri_bot";
const char* mqtt_pass = "kHongbieT31";

// ================= HARDWARE PINS =================
#define DHT_PIN           4
#define SOIL_PIN          34
#define LIGHT_PIN_A       32
#define LIGHT_PIN_D       33
#define PUMP_PIN          18
#define LED_PIN           19

#define DHT_TYPE DHT11
DHT dht(DHT_PIN, DHT_TYPE);

WiFiClientSecure espClient;
PubSubClient client(espClient);

// ================= MEMORY (EEPROM) =================
#define EE_ADDR_DURATION        0
#define EE_ADDR_COOLDOWN        1
#define EE_ADDR_WATER_ENABLED   2
#define EE_ADDR_WATER_THRESHOLD 3  
#define EE_ADDR_LIGHT_ENABLED   7
#define EE_ADDR_LIGHT_THRESHOLD 8  

// ================= INTERVALS =================
#define SEND_INTERVAL 5000

// 🔧 FreeRTOS: Mutexes để bảo vệ biến toàn cục và MQTT client
SemaphoreHandle_t stateMutex;
SemaphoreHandle_t mqttMutex; // Dùng Recursive Mutex

// ================= GLOBAL STATE (Protected by stateMutex) =================
bool pumpOn = false;
bool lightOn = false;
bool manualLightControl = false;  
bool deviceOnlineSent = false;

struct {
  bool enabled = false;
  float threshold = 30;
  int duration = 30;
  int cooldown = 3600;
  unsigned long lastIrrigationTime = 0;
} autoWater;

struct {
  bool enabled = false;
  int threshold = 300;
} autoLight;

bool irrigating = false;
unsigned long irrigationStart = 0;
int irrigationDuration = 0;

// ===================================================
// UTILS & SENSORS
// ===================================================
String getDeviceID(){
  String mac = WiFi.macAddress();
  mac.replace(":", "");
  mac.toLowerCase();
  return "esp_" + mac;
}

float readLightLux() {
  int rawValue = analogRead(LIGHT_PIN_A);
  float normalizedValue = (4095 - rawValue) / 4095.0;
  float lux = pow(normalizedValue, 1.5) * 10000.0;
  return constrain(lux, 0, 10000);
}
float readTemperature(){ float t=dht.readTemperature(); return isnan(t)?-999:t; }
float readHumidity(){ float h=dht.readHumidity(); return isnan(h)?-999:h; }
float readSoil(){ return constrain(map(analogRead(SOIL_PIN),4095,0,0,100),0,100); }

// ===================================================
// MQTT PUBLISHERS
// ===================================================
void publishStatus(String event){
  DynamicJsonDocument doc(600);
  
  xSemaphoreTake(stateMutex, portMAX_DELAY);
  doc["deviceId"] = DEVICE_ID;
  doc["event"] = event;
  doc["pumpOn"] = pumpOn;
  doc["lightOn"] = lightOn;
  doc["manualLightControl"] = manualLightControl;  
  doc["autoMode"] = autoWater.enabled;
  doc["soilMoisture"] = readSoil();
  doc["timestamp"] = millis();

  if(event == "auto_mode_updated"){
    JsonObject cfg = doc.createNestedObject("autoConfig");
    cfg["enabled"] = autoWater.enabled;
    cfg["threshold"] = autoWater.threshold;
    cfg["duration"] = autoWater.duration;
    cfg["cooldown"] = autoWater.cooldown;
    cfg["lastIrrigationTime"] = autoWater.lastIrrigationTime;
  }
  if(event == "light_auto_updated"){
    JsonObject cfg = doc.createNestedObject("config");
    cfg["enabled"] = autoLight.enabled;
    cfg["threshold"] = autoLight.threshold;
  }
  if(event == "irrigation_started"){
    doc["duration"] = irrigationDuration;
  }
  xSemaphoreGive(stateMutex);

  String out; serializeJson(doc,out);
  
  xSemaphoreTakeRecursive(mqttMutex, portMAX_DELAY);
  client.publish(("sensors/"+DEVICE_ID+"/status").c_str(), out.c_str());
  xSemaphoreGiveRecursive(mqttMutex);
}

void publishStatusWithDuration(String event, int duration){
  DynamicJsonDocument doc(600);
  xSemaphoreTake(stateMutex, portMAX_DELAY);
  doc["deviceId"]=DEVICE_ID;
  doc["event"]=event;
  doc["pumpOn"]=pumpOn;
  doc["autoMode"]=autoWater.enabled;
  doc["duration"]=duration;
  doc["soilMoisture"]=readSoil();
  doc["timestamp"]=millis();
  xSemaphoreGive(stateMutex);
  
  String out; serializeJson(doc,out);
  xSemaphoreTakeRecursive(mqttMutex, portMAX_DELAY);
  client.publish(("sensors/"+DEVICE_ID+"/status").c_str(), out.c_str());
  xSemaphoreGiveRecursive(mqttMutex);
}

void publishSensorData(){
  DynamicJsonDocument doc(400);
  doc["deviceId"]=DEVICE_ID;
  doc["secret"]=MQTT_SECRET;
  doc["temperature"]=readTemperature();
  doc["humidity"]=readHumidity();
  doc["soilMoisture"]=readSoil();
  doc["lightLevel"]=readLightLux();
  doc["timestamp"]=millis();
  
  String out; serializeJson(doc,out);
  xSemaphoreTakeRecursive(mqttMutex, portMAX_DELAY);
  client.publish(("sensors/"+DEVICE_ID+"/data").c_str(), out.c_str());
  xSemaphoreGiveRecursive(mqttMutex);
}

// ===================================================
// COMMAND HANDLING
// ===================================================
void handleCommand(DynamicJsonDocument &doc){
  if(doc["secret"]!=MQTT_SECRET) return;
  
  String action = doc["action"] | "";
  String component = doc["component"] | "";

  if(action == "set_auto_mode"){
    xSemaphoreTake(stateMutex, portMAX_DELAY);
    if(doc.containsKey("enabled")) autoWater.enabled = doc["enabled"].as<bool>();
    if(doc.containsKey("threshold")) autoWater.threshold = doc["threshold"].as<float>();
    if(doc.containsKey("duration")) autoWater.duration = doc["duration"].as<int>();
    if(doc.containsKey("cooldown")) autoWater.cooldown = doc["cooldown"].as<int>();
    xSemaphoreGive(stateMutex);
    
    EEPROM.write(EE_ADDR_DURATION, autoWater.duration);
    EEPROM.write(EE_ADDR_COOLDOWN, autoWater.cooldown);
    EEPROM.write(EE_ADDR_WATER_ENABLED, autoWater.enabled ? 1 : 0);
    EEPROM.put(EE_ADDR_WATER_THRESHOLD, autoWater.threshold);
    EEPROM.commit();
    publishStatus("auto_mode_updated");
    return;
  }

  if(action=="turn_on" && component=="pump"){
    xSemaphoreTake(stateMutex, portMAX_DELAY);
    irrigating = false;
    irrigationDuration = 0;
    pumpOn = true;
    digitalWrite(PUMP_PIN,HIGH);
    xSemaphoreGive(stateMutex);
    publishStatus("pump_on");
    return;
  }

  if(action=="turn_off" && component=="pump"){
    xSemaphoreTake(stateMutex, portMAX_DELAY);
    irrigating = false;
    pumpOn = false;
    digitalWrite(PUMP_PIN,LOW);
    autoWater.lastIrrigationTime = millis();
    xSemaphoreGive(stateMutex);
    publishStatus("pump_off");
    return;
  }

  if(action == "irrigate"){
    xSemaphoreTake(stateMutex, portMAX_DELAY);
    irrigationDuration = doc.containsKey("duration") ? doc["duration"].as<int>() : autoWater.duration;
    irrigating = true;
    pumpOn = true;
    irrigationStart = millis();
    digitalWrite(PUMP_PIN,HIGH);
    xSemaphoreGive(stateMutex);
    publishStatus("irrigation_started");
    return;
  }

  if(action=="turn_on_light"){ 
    xSemaphoreTake(stateMutex, portMAX_DELAY);
    manualLightControl = true;
    lightOn=true; 
    digitalWrite(LED_PIN,HIGH); 
    xSemaphoreGive(stateMutex);
    publishStatus("light_on"); 
    return; 
  }
  
  if(action=="turn_off_light"){ 
    xSemaphoreTake(stateMutex, portMAX_DELAY);
    manualLightControl = true;
    lightOn=false; 
    digitalWrite(LED_PIN,LOW); 
    xSemaphoreGive(stateMutex);
    publishStatus("light_off"); 
    return; 
  }

  if(action=="set_light_auto"){
    xSemaphoreTake(stateMutex, portMAX_DELAY);
    if(doc.containsKey("enabled")) {
      bool newEnabled = doc["enabled"].as<bool>();
      if(!newEnabled && lightOn && !manualLightControl) {
        lightOn = false;
        digitalWrite(LED_PIN, LOW);
      }
      autoLight.enabled = newEnabled;
    }
    if(doc.containsKey("threshold")) autoLight.threshold = doc["threshold"].as<int>();
    if(autoLight.enabled) manualLightControl = false;
    xSemaphoreGive(stateMutex);
    
    EEPROM.write(EE_ADDR_LIGHT_ENABLED, autoLight.enabled ? 1 : 0);
    EEPROM.put(EE_ADDR_LIGHT_THRESHOLD, autoLight.threshold);
    EEPROM.commit();
    publishStatus("light_auto_updated");
    return;
  }
}

void callback(char* topic, byte* payload, unsigned int length){
  DynamicJsonDocument doc(512);
  if(!deserializeJson(doc,payload,length)) {
    handleCommand(doc);
  }
}

// ===================================================
// FREERTOS TASKS (CHỈ CÒN ĐIỀU KHIỂN & ĐỌC CẢM BIẾN)
// ===================================================

void ControlTask(void *pvParameters) {
  TickType_t xLastWakeTime = xTaskGetTickCount();
  for(;;) {
    float soil = readSoil();
    float lux = readLightLux();

    bool trigger_irrigation_done = false;
    int completed_duration = 0;
    bool trigger_irrigation_start = false;
    bool trigger_light_on = false;
    bool trigger_light_off = false;

    xSemaphoreTake(stateMutex, portMAX_DELAY);

    if(irrigating){
      int remain = irrigationDuration - (millis() - irrigationStart)/1000;
      if(remain <= 0){
        completed_duration = irrigationDuration;
        irrigating = false;
        pumpOn = false;
        digitalWrite(PUMP_PIN, LOW);
        autoWater.lastIrrigationTime = millis();
        trigger_irrigation_done = true;
      }
    }

    if(autoWater.enabled && !irrigating && !pumpOn){
      bool cooldownOK = (millis()-autoWater.lastIrrigationTime) >= (autoWater.cooldown * 1000);
      if(soil < autoWater.threshold && cooldownOK){
        irrigating=true;
        irrigationStart=millis();
        irrigationDuration=autoWater.duration;
        pumpOn=true;
        digitalWrite(PUMP_PIN,HIGH);
        trigger_irrigation_start = true;
      }
    }

    if(autoLight.enabled && !manualLightControl){
      if(lux < autoLight.threshold && !lightOn){ 
        lightOn=true; 
        digitalWrite(LED_PIN,HIGH); 
        trigger_light_on = true;
      }
      else if(lux > autoLight.threshold && lightOn){ 
        lightOn=false; 
        digitalWrite(LED_PIN,LOW); 
        trigger_light_off = true;
      }
    }

    xSemaphoreGive(stateMutex);

    if(trigger_irrigation_done) {
      publishSensorData();
      publishStatusWithDuration("irrigation_completed", completed_duration);
    }
    if(trigger_irrigation_start) {
      publishSensorData();
      publishStatus("irrigation_started");
    }
    if(trigger_light_on) {
      publishSensorData();
      publishStatus("light_on");
    }
    if(trigger_light_off) {
      publishSensorData();
      publishStatus("light_off");
    }

    vTaskDelayUntil(&xLastWakeTime, pdMS_TO_TICKS(100));
  }
}

void TelemetryTask(void *pvParameters) {
  for(;;) {
    publishSensorData();
    vTaskDelay(pdMS_TO_TICKS(SEND_INTERVAL)); 
  }
}

// ===================================================
// SETUP
// ===================================================
void setup(){
  Serial.begin(115200);
  WRITE_PERI_REG(RTC_CNTL_BROWN_OUT_REG, 0);

  pinMode(PUMP_PIN, OUTPUT);
  pinMode(LED_PIN, OUTPUT);
  digitalWrite(PUMP_PIN, LOW);

  stateMutex = xSemaphoreCreateMutex();
  mqttMutex = xSemaphoreCreateRecursiveMutex();

  EEPROM.begin(64);
  autoWater.duration = EEPROM.read(EE_ADDR_DURATION)==0xFF ? 30 : EEPROM.read(EE_ADDR_DURATION);
  autoWater.cooldown = EEPROM.read(EE_ADDR_COOLDOWN)==0xFF ? 3600 : EEPROM.read(EE_ADDR_COOLDOWN);
  byte waterEnabled = EEPROM.read(EE_ADDR_WATER_ENABLED);
  autoWater.enabled = (waterEnabled == 0xFF) ? false : (waterEnabled == 1);
  float waterThreshold; EEPROM.get(EE_ADDR_WATER_THRESHOLD, waterThreshold);
  autoWater.threshold = (isnan(waterThreshold) || waterThreshold == 0) ? 30.0 : waterThreshold;
  
  byte lightEnabled = EEPROM.read(EE_ADDR_LIGHT_ENABLED);
  autoLight.enabled = (lightEnabled == 0xFF) ? false : (lightEnabled == 1);
  int lightThreshold; EEPROM.get(EE_ADDR_LIGHT_THRESHOLD, lightThreshold);
  autoLight.threshold = (lightThreshold == 0 || lightThreshold == -1) ? 300 : lightThreshold;

  dht.begin();
  
  // 🔌 WI-FI INIT (CHẠY TRỰC TIẾP TRONG SETUP - GIỐNG CODE TEST)
  Serial.println("\n--- Khởi tạo Wi-Fi ---");
  WiFi.mode(WIFI_STA);
  WiFi.disconnect(); 
  delay(500);
  WiFi.begin(ssid, wifi_password);

  int attempts = 0;
  while (WiFi.status() != WL_CONNECTED) {
    delay(1000);
    Serial.print(".");
    attempts++;
    if (attempts >= 30) {
      Serial.println("\n❌ Mạng không phản hồi, thử kết nối lại...");
      attempts = 0;
      WiFi.disconnect();
      delay(500);
      WiFi.begin(ssid, wifi_password);
    }
  }
  Serial.println("\n✅ Wi-Fi Connected!");
  Serial.print("IP: "); Serial.println(WiFi.localIP());

  // CẤU HÌNH MQTT
  DEVICE_ID = getDeviceID();
  Serial.println("DEVICE ID: " + DEVICE_ID);

  espClient.setInsecure();
  client.setServer(mqtt_server,mqtt_port);
  client.setBufferSize(512); 
  client.setCallback(callback);

  // CHỈ TẠO 2 TASK CỦA NGOẠI VI (KHÔNG TẠO NETWORK TASK)
  xTaskCreatePinnedToCore(ControlTask,   "Control",   4096, NULL, 1, NULL, 1); 
  xTaskCreatePinnedToCore(TelemetryTask, "Telemetry", 4096, NULL, 1, NULL, 1); 
}

// ===================================================
// LOOP - XỬ LÝ MẠNG TẠI ĐÂY (CHUẨN ARDUINO)
// ===================================================
void loop(){
  // 1. Duy trì kết nối Wi-Fi
  if (WiFi.status() != WL_CONNECTED) {
    Serial.println("\n Mất kết nối Wi-Fi, đang dò lại...");
    WiFi.disconnect();
    delay(500);
    WiFi.begin(ssid, wifi_password);
    int attempts = 0;
    while(WiFi.status() != WL_CONNECTED && attempts < 15) {
      delay(1000);
      Serial.print(".");
      attempts++;
    }
    if (WiFi.status() == WL_CONNECTED) Serial.println("\n Đã kết nối lại Wi-Fi!");
  }

  // 2. Duy trì kết nối MQTT và Lắng nghe Server
  if (WiFi.status() == WL_CONNECTED) {
    bool mqttConnected = false;
    
    // Kiểm tra trạng thái an toàn
    xSemaphoreTakeRecursive(mqttMutex, portMAX_DELAY);
    mqttConnected = client.connected();
    xSemaphoreGiveRecursive(mqttMutex);

    if (!mqttConnected) {
      Serial.println(" Mất kết nối MQTT, đang thử lại...");
      // Khóa Mutex để connect
      xSemaphoreTakeRecursive(mqttMutex, portMAX_DELAY);
      if(client.connect(("ID_"+DEVICE_ID).c_str(), mqtt_user, mqtt_pass)) {
        Serial.println(" Đã kết nối lại MQTT!");
        client.subscribe(("control/"+DEVICE_ID+"/command").c_str());
        if(!deviceOnlineSent){ deviceOnlineSent=true; publishStatus("device_online"); }
      }
      xSemaphoreGiveRecursive(mqttMutex);
      delay(2000); // Đợi 2s trước khi thử lại nếu thất bại
    } else {
      // Nếu đang kết nối tốt, cho phép thư viện lắng nghe Data trả về
      xSemaphoreTakeRecursive(mqttMutex, portMAX_DELAY);
      client.loop();
      xSemaphoreGiveRecursive(mqttMutex);
    }
  }

  // Nhường CPU cho các tác vụ khác của hệ thống
  delay(50); 
}