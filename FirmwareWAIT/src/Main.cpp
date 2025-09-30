#include <HTTPClient.h>
#include "ArduinoJson.h"
#include <NTPClient.h>
#include <WiFiUdp.h>
#include <PubSubClient.h>
#include <Adafruit_Sensor.h>
#include <pwmWrite.h>
#include <TinyGPS.h>
#include <TinyGPSPlus.h>
#include <HardwareSerial.h>
#include <Wire.h>

#define GSM_TX_PIN 26  
#define GSM_RX_PIN 27  
#define GSM_BAUD_RATE 9600

SoftwareSerial gsmSerial(GSM_RX_PIN, GSM_TX_PIN); // Reasignación UART


HardwareSerial gpsSerial(1);
int idSensor;
float actuador;
char fechaHora[20];
int16_t AcX, AcY, AcZ, Tmp, GyX, GyY, GyZ;
float ax, ay, az, gx, gy, gz;
float mySTATUS;
bool STATUSBINARY;
int year;
byte month, day, hour, minute, second, hundredths;
unsigned long chars;
unsigned short sentences, failed_checksum;



const int DEVICE_ID = 71;
const int SENSOR_GPS_ID = 71;
const int SENSOR_AC_ID = 71;
const int ACTUADOR_ID = 71;

float valueSensor;


int test_delay = 1500; // so we don't spam the API
boolean describe_tests = true;

// Replace 0.0.0.0 by your server local IP (ipconfig [windows] or ifconfig [Linux o MacOS] gets IP assigned to your PC)




String serverName = "http://172.20.10.2:8080/";


String apn = "web.yoigo.es";          
String gprsUser = "";            
String gprsPass = "";   




HTTPClient http;







#define LED_PIN 27 // pin de placa, donde pone G27
#define TEMPERATURE_THRESHOLD 25.0
//TinyGPS gps;
TinyGPS gps;
#define TXD1 17  
#define RXD1 16

const int MPU_ADDR = 0x68;     // dirección del dispositivo en el bus I2C
const int WHO_AM_I = 0x75;     // registro de identificación del dispositivo
const int PWR_MGMT_1 = 0x6B;   // registro de gestión de energía 
const int GYRO_CONFIG = 0x1B;  //  registro de configuración del giroscopio
const int ACCEL_CONFIG = 0x1C; // registro de configuración del acelerómetro
const int ACCEL_XOUT = 0x3B;   //  registro de lectura de datos del acelerómetro en el eje X
float latitude, longitude;
const int acData_pin = 21; 
const int acClock_pin = 22; 



// NTP (Net time protocol) settings
WiFiUDP ntpUDP;
NTPClient timeClient(ntpUDP);



bool sendATCommand(String command, String expected_response, unsigned long timeout) {
  gsmSerial.println(command);
  
  String response = "";
  unsigned long start = millis();
  
  while (millis() - start < timeout) {
    if (gsmSerial.available()) {
      response += gsmSerial.readString();
    }
    
    if (response.indexOf(expected_response) != -1) {
      Serial.println("AT Command: " + command);
      Serial.println("Response: " + response);
      return true;
    }
  }
  
  Serial.println("AT Command Failed: " + command);
  Serial.println("Response: " + response);
  return false;
}

bool initGSM() {
  Serial.println("Inicializando módulo GSM...");
  
  gsmSerial.begin(GSM_BAUD_RATE);
  delay(3000);
  
  
  gsmSerial.println("AT+CPIN?");
  delay(2000);
  String response = "";
  while(gsmSerial.available()) {
    response += gsmSerial.readString();
  }
  
  
  if (response.indexOf("SIM PIN") != -1) {
    Serial.println("SIM requiere PIN...");
    String pinCommand = "AT+CPIN=3117";  
    if (!sendATCommand(pinCommand, "OK", 5000)) {
      Serial.println("Error: PIN incorrecto");
      return false;
    }
    delay(3000); 
  }
  gsmSerial.begin(GSM_BAUD_RATE);
  delay(3000);
  
  // Limpiar buffer
  while(gsmSerial.available()) {
    gsmSerial.read();
  }
  
  Serial.println("Enviando comandos AT...");
  
  
  if (!sendATCommand("AT", "OK", 3000)) {
    Serial.println("Error: GSM no responde");
    return false;
  }
  
  delay(500);
  
  if (!sendATCommand("AT+CPIN?", "READY", 8000)) {
    Serial.println("Error: SIM no está lista");
    return false;
  }
  
  delay(1000);
  
  if (!sendATCommand("AT+CREG?", "+CREG: 0,1", 15000)) {
    Serial.println("Error: No registrado en la red");
    return false;
  }
  
  delay(1000);
  
  if (!sendATCommand("AT+CGATT=1", "OK", 15000)) {
    Serial.println("Error: No se pudo activar GPRS");
    return false;
  }
  
  delay(1000);
  
  // Configurar APN
  String apnCommand = "AT+CSTT=\"" + apn + "\",\"" + gprsUser + "\",\"" + gprsPass + "\"";
  if (!sendATCommand(apnCommand, "OK", 8000)) {
    Serial.println("Error: No se pudo configurar APN");
    return false;
  }
  
  delay(2000);
  
  if (!sendATCommand("AT+CIICR", "OK", 35000)) {
    Serial.println("Error: No se pudo establecer conexión GPRS");
    return false;
  }
  
  delay(2000);
  
  if (!sendATCommand("AT+CIFSR", ".", 8000)) {
    Serial.println("Error: No se pudo obtener IP");
    return false;
  }
  
  Serial.println("GSM inicializado correctamente");
  return true;
}


bool sendHTTPRequest(String url, String method, String data = "") {
  // Cerrar cualquier conexión previa
  sendATCommand("AT+CIPCLOSE", "OK", 2000);
  delay(1000);
  
  // Extraer host y path de la URL
  String host = "";
  String path = "";
  int port = 80;
  
  
  int pathIndex = url.indexOf('/');
  if (pathIndex > 0) {
    host = url.substring(0, pathIndex);
    path = url.substring(pathIndex);
  } else {
    host = url;
    path = "/";
  }
  
  
  int portIndex = host.indexOf(':');
  if (portIndex > 0) {
    port = host.substring(portIndex + 1).toInt();
    host = host.substring(0, portIndex);
  }
  
  Serial.println("Host: " + host + ", Port: " + String(port) + ", Path: " + path);
  
  
  String connectCommand = "AT+CIPSTART=\"TCP\",\"" + host + "\"," + String(port);
  if (!sendATCommand(connectCommand, "CONNECT OK", 20000)) {
    Serial.println("Error: No se pudo conectar al servidor");
    return false;
  }
  
  // Preparar solicitud HTTP
  String httpRequest = method + " " + path + " HTTP/1.1\r\n";
  httpRequest += "Host: " + host + "\r\n";
  httpRequest += "Connection: close\r\n";
  
  if (method == "POST" && data.length() > 0) {
    httpRequest += "Content-Type: application/json\r\n";
    httpRequest += "Content-Length: " + String(data.length()) + "\r\n";
    httpRequest += "\r\n";
    httpRequest += data;
  } else {
    httpRequest += "\r\n";
  }
  
  // Enviar datos
  String sendCommand = "AT+CIPSEND=" + String(httpRequest.length());
  gsmSerial.println(sendCommand);
  
  // Esperar prompt ">"
  unsigned long start = millis();
  while (millis() - start < 5000) {
    if (gsmSerial.available() && gsmSerial.read() == '>') {
      break;
    }
  }
  
  
  gsmSerial.print(httpRequest);
  
  
  String response = "";
  start = millis();
  while (millis() - start < 15000) {
    if (gsmSerial.available()) {
      response += gsmSerial.readString();
    }
    
    if (response.indexOf("SEND OK") != -1) {
      Serial.println("Solicitud HTTP enviada correctamente");
      Serial.println("Respuesta: " + response);
      
      // Cerrar conexión
      sendATCommand("AT+CIPCLOSE", "OK", 2000);
      return true;
    }
  }
  
  Serial.println("Error: Timeout en solicitud HTTP");
  Serial.println("Respuesta: " + response);
  sendATCommand("AT+CIPCLOSE", "OK", 2000);
  return false;
}


void initI2C()
{
  //Serial.println("---inside initI2C");
  Wire.begin(acData_pin, acClock_pin);
}

void writeRegMPU(int reg, int val) //Escribe un valor en un reg especifico del MPU
{
  Wire.beginTransmission(MPU_ADDR);  // Inicia la comunicación I2C con el MPU6050 en la dirección especificada (MPU_ADDR)
  Wire.write(reg);                   // Envía el registro al que se desea acceder (registro donde escribirás el valor)
  Wire.write(val);                   // Envía el valor que deseas escribir en ese registro
  Wire.endTransmission(true);        // Finaliza la transmisión, completando la operación de escritura
}


uint8_t readRegMPU(uint8_t reg) // Funcion para leer un registro
{
  uint8_t data;
  Wire.beginTransmission(MPU_ADDR);   // Inicia la comunicación I2C con el MPU6050
  Wire.write(reg);                    // Envía el registro del cual se desea leer
  Wire.endTransmission(false);        // Termina la transmisión pero mantiene la comunicación abierta para leer
  Wire.requestFrom(MPU_ADDR, 1);      // Solicita 1 byte del registro solicitado
  data = Wire.read();                 // Lee el byte recibido y lo guarda en la variable 'data'
  return data;                         // Devuelve el byte leído desde el registro
}


/*
 * Función para buscar el sensor en la dirección 0x68
 */
void findMPU(int mpu_addr)
{
  Wire.beginTransmission(MPU_ADDR);
  int data = Wire.endTransmission(true);

  if (data == 0)
  {
    Serial.print("Dispositivo encontrado en la direccion: 0x");
    Serial.println(MPU_ADDR, HEX);
  }
  else
  {
    Serial.println("Dispositivo no encontrado!");
  }
}

/*
 * Función que verifica si el sensor responde correctamente y si está activo
 */
void checkMPU(int mpu_addr)
{
  findMPU(MPU_ADDR);

  int data = readRegMPU(WHO_AM_I); // Register 117 – Who Am I - 0x75

  if (data == 104)
  {
    Serial.println("MPU6050 Dispositivo respondió OK! (104)");

    data = readRegMPU(PWR_MGMT_1); // Register 107 – Power Management 1-0x6B

    if (data == 64)
      Serial.println("MPU6050 en modo SLEEP! (64)");
    else
      Serial.println("MPU6050 en modo ACTIVE!");
  }
  else
    Serial.println("Verifique el dispositivo - ¡MPU6050 NO disponible!");
}

/*
 * Función para inicializar el sensor
 */


void setAccelScale()
{
  writeRegMPU(ACCEL_CONFIG, 0);
}

void setGyroScale()
{
  writeRegMPU(GYRO_CONFIG, 0);
}

/* 
 * Función para desactivar el modo de sueño del sensor y activarlo
 */
void setSleepOff()
{
  writeRegMPU(PWR_MGMT_1, 0); // Escribe 0 en el registro PWR_MGMT_1 (0x6B) para desactivar el modo de sueño
}

void initMPU()
{
  setSleepOff();
  setGyroScale();
  setAccelScale();
}



uint32_t delayMS;
// Setup

unsigned long startTime;
void setup() {
  Serial.begin(115200);
  gpsSerial.begin(9600, SERIAL_8N1, RXD1, TXD1);
  
  Serial.println("Iniciando sistema...");
  
  pinMode(LED_PIN, OUTPUT);
  initI2C();
  initMPU();
  checkMPU(MPU_ADDR);
  
  
  if (initGSM()) {
    Serial.println("Sistema GSM listo!");
  } else {
    Serial.println("Error: No se pudo inicializar GSM");
  }
  
  Serial.println("Setup completado!");
}

String response;

String serializeSensorGpsStatesBody(int idSensorGps, char fechaHora[20], float valueLong, float valueLat)
{
  // StaticJsonObject allocates memory on the stack, it can be
  // replaced by DynamicJsonDocument which allocates in the heap.
  
  //
  DynamicJsonDocument doc(2048);

  // Add values in the document
  //
  doc["idSensorGps"] = idSensorGps;
  doc["fechaHora"] = fechaHora;
  doc["valueLong"] = valueLong;
  doc["valueLat"] = valueLat;
  doc["removed"] = false;

  // Generate the minified JSON and send it to the Serial port.
  //
  String output;
  serializeJson(doc, output);
  Serial.println(output);

  return output;
}

String serializeSensorGpsBody(int idDevice)
{
  // StaticJsonObject allocates memory on the stack, it can be
  // replaced by DynamicJsonDocument which allocates in the heap.
  
  //
  DynamicJsonDocument doc(2048);

  // Add values in the document
  //
  doc["idDevice"] = idDevice;
  doc["removed"] = false;

  // Generate the minified JSON and send it to the Serial port.
  //
  String output;
  serializeJson(doc, output);
  Serial.println(output);

  return output;
}


String serializeSensorACStatesBody(int idSensorAC, int valueAc, int valueGir)
{
  DynamicJsonDocument doc(2048);

  doc["idSensorAC"] = idSensorAC;
  doc["valueAc"] = valueAc;
  doc["valueGir"] = valueGir;
  doc["removed"] = false;

  String output;
  serializeJson(doc, output);
  return output;

}

String serializeSensorACBody(int idDevice)
{
  DynamicJsonDocument doc(2048);

  doc["idDevice"] = idDevice;
  doc["removed"] = false;

  String output;
  serializeJson(doc, output);
  return output;

}

String serializeDeviceBody(String deviceSerialId, String name)
{
  DynamicJsonDocument doc(2048);

  doc["deviceSerialId"] = deviceSerialId;
  doc["name"] = name;

  String output;
  serializeJson(doc, output);
  return output;
}

String serializeActuatorStatusBody(float status, bool statusBinary, int idActuator, long timestamp)
{
  DynamicJsonDocument doc(2048);

  doc["status"] = status;
  doc["statusBinary"] = statusBinary;
  doc["idActuator"] = idActuator;
  doc["timestamp"] = timestamp;
  doc["removed"] = false;

  String output;
  serializeJson(doc, output);
  return output;

}





void deserializeActuatorStatusBody(String responseJson)
{
  if (responseJson != "")
  {
    DynamicJsonDocument doc(2048);

    // Deserialize the JSON document
    DeserializationError error = deserializeJson(doc, responseJson);

    // Test if parsing succeeds.
    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      return;
    }

    // Fetch values.
    int idActuatorState = doc["idActuatorState"];
    float status = doc["status"];
    bool statusBinary = doc["statusBinary"];
    int idActuator = doc["idActuator"];
    long timestamp = doc["timestamp"];

    Serial.println(("Actuator status deserialized: [idActuatorState: " + String(idActuatorState) + ", status: " + String(status) + ", statusBinary: " + String(statusBinary) + ", idActuator" + String(idActuator) + ", timestamp: " + String(timestamp) + "]").c_str());
  }
}

void deserializeSensorACStatesBody(int httpResponseCode)
{

  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String responseJson = http.getString();
    DynamicJsonDocument doc(2048);

    DeserializationError error = deserializeJson(doc, responseJson);

    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      return;
    }

    int idSensorACStates = doc["idSensorACStates"];
    int idSensorAC = doc["idSensorAC"];
    int valueAc = doc["valueAc"];
    int valueGir = doc["valueGir"];
    

    Serial.println(("Device deserialized: [idSensorACStates: " + String(idSensorACStates) + ", idSensorAC: " + String(idSensorAC) +  ", valueAc: " + String(valueAc) + ", valueGir: " + String(valueGir) + "]").c_str());
  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }
}

void deserializeSensorACBody(int httpResponseCode)
{

  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String responseJson = http.getString();
    DynamicJsonDocument doc(2048);

    DeserializationError error = deserializeJson(doc, responseJson);

    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      return;
    }

    int idSensorAC = doc["idSensorAC"];
    int idDevice = doc["idDevice"];
   
    

    Serial.println(("Device deserialized: [idSensorAC: " + String(idSensorAC) + ", idDevice: " + String(idDevice) + "]").c_str());
  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }
}

void deserializeSensorGpsStatesBody(int httpResponseCode)
{

  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String responseJson = http.getString();
    DynamicJsonDocument doc(2048);

    DeserializationError error = deserializeJson(doc, responseJson);

    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      return;
    }

    int idSensorGpsStates = doc["idSensorGpsStates"];
    int idSensorGps = doc["idSensorGps"];
    unsigned long fechaHora = doc["fechaHora"];
    float valueLong = doc["valueLong"];
    float valueLat = doc["valueLat"];
    

    Serial.println(("Device deserialized: [idSensorGpsStates: " + String(idSensorGpsStates) + ", idSensorGps: " + String(idSensorGps) +  ", fechaHora: " + String(fechaHora) + ", valueLong: " + String(valueLong) + ", valueLat: " + String(valueLat) + "]").c_str());
  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }
}

void deserializeSensorGpsBody(int httpResponseCode)
{

  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String responseJson = http.getString();
    DynamicJsonDocument doc(2048);

    DeserializationError error = deserializeJson(doc, responseJson);

    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      return;
    }

    int idSensorGps = doc["idSensorGps"];
    int idDevice = doc["idDevice"];
    

    Serial.println(("Device deserialized: [idSensorAC: " + String(idSensorGps) + ", idDevice: " + String(idDevice) + "]").c_str());
  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }
}

void deserializeDeviceBody(int httpResponseCode)
{

  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String responseJson = http.getString();
    DynamicJsonDocument doc(2048);

    DeserializationError error = deserializeJson(doc, responseJson);

    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      return;
    }

    int idDevice = doc["idDevice"];
    String deviceSerialId = doc["deviceSerialId"];
    String name = doc["name"];
    

    Serial.println(("Device deserialized: [idDevice: " + String(idDevice) + ", name: " + name + ", deviceSerialId: " + deviceSerialId + "]").c_str());
  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }
}

void deserializeSensorsAcStatesFromDevice(int httpResponseCode)
{

  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String responseJson = http.getString();
    
    DynamicJsonDocument doc(ESP.getMaxAllocHeap());

    // parse a JSON array
    DeserializationError error = deserializeJson(doc, responseJson);

    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      return;
    }

    // extract the values
    JsonArray array = doc.as<JsonArray>();

    
    for (JsonObject sensor : array)
    {
      int idSensorACStates = sensor["idSensorACStates"];
      int valueAc = sensor["valueAc"];
      int valueGir = sensor["valueGir"];
      int idSensorAC = sensor["idSensorAC"];

      

      Serial.println(("Sensor deserialized: [idSensorACStates: " + String(idSensorACStates) + ", valueAc: " + String(valueAc) +  ", valueGir: " + String(valueGir) + ", idSensorAC: " + String(idSensorAC) + "]").c_str());
    }
  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }
}

void deserializeSensorsGpsStatesFromDevice(int httpResponseCode)
{

  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String responseJson = http.getString();
    
    DynamicJsonDocument doc(ESP.getMaxAllocHeap());

    // parse a JSON array
    DeserializationError error = deserializeJson(doc, responseJson);

    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      return;
    }

    // extract the values
    JsonArray array = doc.as<JsonArray>();

    
    for (JsonObject sensor : array)
    {
      int idSensorGpsStates = sensor["idSensorGpsStates"];
      unsigned long fechaHora = sensor["fechaHora"];
      float valueLong = sensor["valueLong"];
      float valueLat = sensor["valueLat"];
      int idSensorGps = sensor["idSensorGps"];


      

      Serial.println(("Sensor deserialized: [idSensorGpsStates: " + String(idSensorGpsStates) + ", fechaHora: " + String(fechaHora) +  ", valueLong: " + String(valueLong) + ", valueLat: " + String(valueLat) + ", idSensorGps: " + String(idSensorGps) + "]").c_str());
    }
  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }
}



void deserializeActuatorsFromDevice(int httpResponseCode)
{

  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String responseJson = http.getString();
    // allocate the memory for the document
    DynamicJsonDocument doc(ESP.getMaxAllocHeap());

    // parse a JSON array
    DeserializationError error = deserializeJson(doc, responseJson);

    if (error)
    {
      Serial.print(F("deserializeJson() failed: "));
      Serial.println(error.f_str());
      return;
    }

    // extract the values
    JsonArray array = doc.as<JsonArray>();
    for (JsonObject sensor : array)
    {
      
      int idActuator = sensor["idActuator"];
      String name = sensor["name"];
      int idDevice = sensor["idDevice"];
      
      Serial.println(("Actuator deserialized: [idActuator: " + String(idActuator) + ", name: " + name + ", idDevice: " + String(idDevice) + "]").c_str());
  }
  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }


}



void test_response(int httpResponseCode)
{
  delay(test_delay);
  if (httpResponseCode > 0)
  {
    Serial.print("HTTP Response code: ");
    Serial.println(httpResponseCode);
    String payload = http.getString();
    Serial.println(payload);
  }
  else
  {
    Serial.print("Error code: ");
    Serial.println(httpResponseCode);
  }
}

void describe(char *description)
{
  if (describe_tests)
    Serial.println(description);
}


void POST_tests()
{

  String actuator_states_body = serializeActuatorStatusBody(mySTATUS, STATUSBINARY, ACTUADOR_ID, millis());
  describe("Test POST with actuator state via GSM");
  String serverPath = serverName + "api/actuator_states";
  sendHTTPRequest(serverPath, "POST", actuator_states_body);

  String sensorGpsBody = serializeSensorGpsStatesBody(SENSOR_GPS_ID, fechaHora, latitude, longitude);
  String serverPathGps = serverName + "api/sensorsGpsStates";
  describe("Test POST with GPS state via GSM");
  sendHTTPRequest(serverPathGps, "POST", sensorGpsBody);

  String sensorACBody = serializeSensorACStatesBody(SENSOR_AC_ID, ax, gy);
  String serverPathAc = serverName + "api/sensorsAcStates";
  describe("Test POST with AC state via GSM");
  sendHTTPRequest(serverPathAc, "POST", sensorACBody);

}


  


void readRawMPU()
{
  Wire.beginTransmission(MPU_ADDR);
  Wire.write(ACCEL_XOUT);
  Wire.endTransmission(false);
  Wire.requestFrom(MPU_ADDR, 14);

  AcX = Wire.read() << 8 | Wire.read();
  AcY = Wire.read() << 8 | Wire.read();
  AcZ = Wire.read() << 8 | Wire.read();

  Tmp = Wire.read() << 8 | Wire.read();

  GyX = Wire.read() << 8 | Wire.read();
  GyY = Wire.read() << 8 | Wire.read();
  GyZ = Wire.read() << 8 | Wire.read();

  // Conversión a unidades físicas
  ax = AcX / 16384.0; // Aceleración en g
  ay = AcY / 16384.0;
  az = AcZ / 16384.0;
  
  gx = GyX / 131.0; // Velocidad angular en °/s
  gy = GyY / 131.0;
  gz = GyZ / 131.0;

  Serial.print("AcX = ");
  Serial.print(ax);
  Serial.print("g | AcY = ");
  Serial.print(ay);
  Serial.print("g | AcZ = ");
  Serial.print(az);
  Serial.print("g | Tmp = ");
  Serial.print(Tmp / 340.00 + 36.53);
  Serial.print("°C | GyX = ");
  Serial.print(gx);
  Serial.print("°/s | GyY = ");
  Serial.print(gy);
  Serial.print("°/s | GyZ = ");
  Serial.println(gz);

  delay(50);
}





String trama = "";
int repeticiones;

void loop()
{

if(repeticiones == 1000){

  

  readRawMPU();

  

  
char gpsData = gpsSerial.read();

  while(gpsSerial.available()){
    int c = gpsSerial.read();
    
   
      if(gps.encode(c))  
      {
        
        gps.f_get_position(&latitude, &longitude);
        Serial.print("Latitud/Longitud: "); 
        Serial.print(latitude,5); 
        Serial.print(", "); 
        Serial.println(longitude,5);
  
  
    gps.crack_datetime(&year,&month,&day,&hour,&minute,&second,&hundredths);
        Serial.print("Fecha: "); Serial.print(day, DEC); Serial.print("/"); 
        Serial.print(month, DEC); Serial.print("/"); Serial.print(year);
        Serial.print(" Hora: "); Serial.print(hour, DEC); Serial.print(":"); 
        Serial.print(minute, DEC); Serial.print(":"); Serial.print(second, DEC); 
        Serial.print("."); Serial.println(hundredths, DEC);
        Serial.print("Altitud (metros): ");
        Serial.println(gps.f_altitude()); 
        Serial.print("Rumbo (grados): "); Serial.println(gps.f_course()); 
        Serial.print("Velocidad(kmph): ");
        Serial.println(gps.f_speed_kmph());
        Serial.print("Satelites: "); Serial.println(gps.satellites());
        Serial.println();
        gps.stats(&chars, &sentences, &failed_checksum);
        snprintf(fechaHora, sizeof(fechaHora), "%04d-%02d-%02d %02d:%02d:%02d",
         year, month, day, hour, minute, second);
        
      }
      POST_tests();
    }

  delay(test_delay); 
    
}else{
    repeticiones++;
}
//timeClient.update();

}

