#include <ESP8266WiFi.h>
#include <DNSServer.h>
#include <WiFiManager.h>  // https://github.com/tzapu/WiFiManager
#include <SoftwareSerial.h>
#include <Firebase_ESP_Client.h>

// Provide the token generation process info
#include <addons/TokenHelper.h>
// Provide the RTDB payload printing info and other helper functions
#include <addons/RTDBHelper.h>

// Insert Firebase project API Key
#define API_KEY "AIzaSyDqWFohe63HlFyPWSCTTFiuxV5M6F3SXKE"
#define DATABASE_URL "https://up-oblation-diorama-app-default-rtdb.asia-southeast1.firebasedatabase.app"

#define RXp2 4
#define TXp2 5

#define RESET_WIFI_PIN 0 //changenow
#define VSENSOR_PIN A0

#define relayLaserLeft1 14
#define relayLaserLeft2 12
#define relayLaserRight1 13
#define relayLaserRight2 15
#define relaySpotlight 16
#define relayLightPost 2

// Interval constants
const uint16_t interval_5ns = 500;
const uint16_t interval_1s = 1000;
const uint16_t interval_2s = 2000;
const uint16_t interval_3s = 3000;
const uint16_t interval_5s = 5000;

int randomDelay1 = interval_3s;
int randomDelay2 = interval_3s;

// Replaced String with char array for memory efficiency
char deviceMode[10] = "ambient"; //ambient
char leftLaserMode[10] = "steady";
char rightLaserMode[10] = "steady";

// lightpost and spotlight are on/off only
bool leftLaserState = false;
bool rightLaserState = false;
bool spotlightState = false;
bool lightpostState = false;

// Time delays
unsigned long currentMillis = 0;
unsigned long leftLaserMillis = 0;
unsigned long rightLaserMillis = 0;
unsigned long vsensorMillis = 0;

const float OFFSET_VALUE = 6.4; 
const float R1 = 30000.0; 
const float R2 = 7500.0;

const int timeout = 120;
unsigned long sendDataPrevMillis = 0;
bool signupOK = false;

float vout = 0.0; 
float vin = 0.0; 

WiFiManager wm;

FirebaseData fbdo1;
FirebaseData fbdo2;
FirebaseAuth auth;
FirebaseConfig config;
SoftwareSerial unoSerial(RXp2, TXp2);

// Callback for stream timeout
void streamTimeoutCallback(bool timeout) {
  if (timeout) {
    Serial.println(F("Stream timeout, resume streaming..."));
  }
}

// Stream callback for custom_controls path
void streamCallback1(FirebaseStream data) {
  String path = data.dataPath();
  String val = data.stringData();

  Serial.println(F("Data changed in custom_controls:"));
  Serial.println("PATH: " + path);
  Serial.println("VALUE: " + val);

  if (path.equals("/leftLedPumpMode")) {
    unoSerial.println("left_led_pump_mode," + val);
  } else if (path.equals("/rightLedPumpMode")) {
    unoSerial.println("right_led_pump_mode," + val);
  } else if (path.equals("/rightLaserMode")) {
    unoSerial.println("right_laser_mode," + val);
  } else if (path.equals("/leftLaserMode")) {
    unoSerial.println("left_laser_mode," + val);
  } else if (path.equals("/spotlightState")) {
    unoSerial.println("spotlight_state," + val);
  } else if (path.equals("/lightpostState")) {
    unoSerial.println("lightpost_state," + val);
  } else if (path.equals("/musicName")) {
    unoSerial.println("music_name," + val);
  }
}

// Stream callback for utilities path
void streamCallback2(FirebaseStream data) {
  String path = data.dataPath();
  String val = data.stringData();

  Serial.println(F("Data changed in utilities:"));
  Serial.println("PATH: " + path);
  Serial.println("VALUE: " + val);

  if (path.equals("/deviceMode")) {
    unoSerial.println("device_mode," + val);
  } else if (path.equals("/soundVolume")) {
    unoSerial.println("sound_volume," + val);
  }
  
}

void setup() {
  Serial.begin(9600);
  unoSerial.begin(19200);

  init_relay_pins();

  Serial.println(F("Wi-Fi not connected, starting WiFiManager..."));
  wm.setTimeout(timeout);
  if (!wm.autoConnect("UP_Plaza_AP")) {
    Serial.println(F("Failed to connect. Restarting..."));
    delay(3000);
    // wm.resetSettings();
    ESP.restart();
  }

  if (WiFi.status() == WL_CONNECTED) {
    delay(1500);
    setup_firebase();
  }

  delay(1500);
  Serial.println(F("ESP8266 Initialized"));
}

void loop() {
  currentMillis = millis();

  if (Serial.available() > 0) {
    String receivedChar = Serial.readStringUntil('\n');
    Serial.print(F("Serial sent: "));
    Serial.println(receivedChar);
    unoSerial.println(receivedChar); 
  }

  if (digitalRead(RESET_WIFI_PIN) == LOW) {
    Serial.println(F("Reset WiFi pin pressed, launching config portal..."));
    launch_wifi_config_portal();
  }

  if (unoSerial.available()) {
    String val = unoSerial.readStringUntil('\n');
    Serial.print("unoSerial.readStringUntil('\n'): ");
    Serial.println(val);
    val = removeNewlines(val);
    if (val.length() != 0) {
      Serial.println(val);
      if (hasComma(val)) {
        String key = convertToKeyValue(val, 0);
        String keyValue = convertToKeyValue(val, 1);

        Serial.print("KEY: ");
        Serial.print(key);
        Serial.print(", VALUE: ");
        Serial.println(keyValue);

        if (key == "device_mode") {
          strcpy(deviceMode, keyValue.c_str());
        } else if (key == "left_laser_mode") {
          strcpy(leftLaserMode, keyValue.c_str());
        } else if (key == "right_laser_mode") {
          strcpy(rightLaserMode, keyValue.c_str());
        } else if (key == "lightpost_state") {
          lightpostState = keyValue.equals("true");
        } else if (key == "spotlight_state") {
          spotlightState = keyValue.equals("true");
        } else if (key == "water_level") {
          Serial.println("Sending water level update to Firebase...");
          if (Firebase.RTDB.setString(&fbdo2, "/up_diorama_01/utilities/waterLevel", keyValue)) {
            Serial.println(F("Data added successfully!"));
          } else {
            Serial.println(F("Failed to add data!"));
            Serial.println("REASON: " + fbdo2.errorReason());
          }
          delay(200);
        } else if (key == "power_source") {
          Serial.println("Sending power source update to Firebase...");
          if (Firebase.RTDB.setString(&fbdo2, "/up_diorama_01/utilities/powerSource", keyValue)) {
            Serial.println(F("Data added successfully!"));
          } else {
            Serial.println(F("Failed to add data!"));
            Serial.println("REASON: " + fbdo2.errorReason());
          }
          delay(200);
        } else if (key == "is_fetching_rtdb") {
          if (keyValue.equals("true")) {
            fetch_rtdb_data();
          }
        }
      } else {
        Serial.print("UNO PRINT: ");
        Serial.println(val);
        delay(200);
      }
    }
  }

  setLaserLeft();
  setLaserRight();
  setSpotlight();
  setLightpost();

  if (WiFi.status() == WL_CONNECTED) {
    if (Firebase.ready() && signupOK && (millis() - sendDataPrevMillis > 60000 || sendDataPrevMillis == 0)) {
      sendDataPrevMillis = millis();
      Serial.println(F("Firebase is ready, sending data if necessary..."));
      checkBattery();
    }
  }
}
//TEEST
void checkBattery() {
  // Update data every 30 seconds
  // if the percentage is equal or less than the minimum voltage, then charge the battery
  // otherwise, update stop charging
  float percentList[5];
  for (int i = 0; i < 5; i++) {
    int sdata = analogRead(VSENSOR_PIN);
    float voltageAtPin = (sdata * 3.3) / 4095.0;
    float batteryVoltage = voltageAtPin * (R1 + R2) / R2;
    if (batteryVoltage <= 0) {
      batteryVoltage -= OFFSET_VALUE;
    }
    int batteryPercentage;

    if (batteryVoltage < 12.89 && batteryVoltage > 12.79) {
      batteryPercentage = mapFloat(batteryVoltage, 12.79, 12.89, 91, 100);
    } else if (batteryVoltage < 12.78 && batteryVoltage > 12.66) {
      batteryPercentage = mapFloat(batteryVoltage, 12.66, 12.78, 81, 90);
    } else if (batteryVoltage < 12.65 && batteryVoltage > 12.77) {
      batteryPercentage = mapFloat(batteryVoltage, 12.52, 12.65, 71, 80);
    } else if (batteryVoltage < 12.51 && batteryVoltage > 12.42) {
      batteryPercentage = mapFloat(batteryVoltage, 12.42, 12.51, 61, 70);
    } else if (batteryVoltage < 12.41 && batteryVoltage > 12.24) {
      batteryPercentage = mapFloat(batteryVoltage, 12.24, 12.41, 51, 60);
    } else if (batteryVoltage < 12.23 && batteryVoltage > 12.12) {
      batteryPercentage = mapFloat(batteryVoltage, 12.12, 12.23, 41, 50);
    } else if (batteryVoltage < 12.11 && batteryVoltage > 11.97) {
      batteryPercentage = mapFloat(batteryVoltage, 11.97, 12.11, 31, 40);
    } else if (batteryVoltage < 11.96 && batteryVoltage > 11.82) {
      batteryPercentage = mapFloat(batteryVoltage, 11.82, 11.96, 21, 30);
    } else if (batteryVoltage < 11.81 && batteryVoltage > 11.71) {
      batteryPercentage = mapFloat(batteryVoltage, 11.71, 11.81, 11, 20);
    } else if (batteryVoltage < 11.70 && batteryVoltage > 11.64) {
      batteryPercentage = mapFloat(batteryVoltage, 11.64, 11.70, 1, 10);
    } else {
      batteryPercentage = 0;
    }
    percentList[i] = batteryPercentage;
  }
  float percentSum = percentList[0] + percentList[1] + percentList[2] + percentList[3] + percentList[4];
  float averagePercentage = percentSum / 5;

  Serial.print("Battery percent: ");
  Serial.print(averagePercentage);
  Serial.println("%");
  Serial.println("Sending batteryPercent update to Firebase...");
  if (Firebase.RTDB.setFloat(&fbdo2, "/up_diorama_01/utilities/batteryPercent", averagePercentage)) {
    Serial.println(F("Data added successfully!"));
  } else {
    Serial.println(F("Failed to add data!"));
    Serial.println("REASON: " + fbdo2.errorReason());
  }
}

int mapFloat(float x, float in_min, float in_max, int out_min, int out_max) {
  return (x - in_min) * (out_max - out_min) / (in_max - in_min) + out_min;
}

void fetch_rtdb_data() {
  Serial.printf("Get leftLaserMode... %s\n", Firebase.RTDB.getString(&fbdo1, "/up_diorama_01/custom_controls/leftLaserMode") ? "ok" : fbdo1.errorReason().c_str());
  unoSerial.println("left_laser_mode," + fbdo1.stringData());
  delay(200);

  Serial.printf("Get leftLedPumpMode... %s\n", Firebase.RTDB.getString(&fbdo1, "/up_diorama_01/custom_controls/leftLedPumpMode") ? "ok" : fbdo1.errorReason().c_str());
  unoSerial.println("left_led_pump_mode," + fbdo1.stringData());
  delay(200);

  Serial.printf("Get lightpostState... %s\n", Firebase.RTDB.getString(&fbdo1, "/up_diorama_01/custom_controls/lightpostState") ? "ok" : fbdo1.errorReason().c_str());
  unoSerial.println("lightpost_state," + fbdo1.stringData());
  delay(200);

  Serial.printf("Get musicName... %s\n", Firebase.RTDB.getString(&fbdo1, "/up_diorama_01/custom_controls/musicName") ? "ok" : fbdo1.errorReason().c_str());
  unoSerial.println("music_name," + fbdo1.stringData());
  delay(200);

  Serial.printf("Get rightLaserMode... %s\n", Firebase.RTDB.getString(&fbdo1, "/up_diorama_01/custom_controls/rightLaserMode") ? "ok" : fbdo1.errorReason().c_str());
  unoSerial.println("right_laser_mode," + fbdo1.stringData());
  delay(200);

  Serial.printf("Get rightLedPumpMode... %s\n", Firebase.RTDB.getString(&fbdo1, "/up_diorama_01/custom_controls/rightLedPumpMode") ? "ok" : fbdo1.errorReason().c_str());
  unoSerial.println("right_led_pump_mode," + fbdo1.stringData());
  delay(200);

  Serial.printf("Get spotlightState... %s\n", Firebase.RTDB.getString(&fbdo1, "/up_diorama_01/custom_controls/spotlightState") ? "ok" : fbdo1.errorReason().c_str());
  unoSerial.println("spotlight_state," + fbdo1.stringData());
  delay(200);

  Serial.printf("Get soundVolume... %s\n", Firebase.RTDB.getString(&fbdo2, "/up_diorama_01/utilities/soundVolume") ? "ok" : fbdo2.errorReason().c_str());
  unoSerial.println("sound_volume," + fbdo2.stringData());
  delay(200);

  unoSerial.println("is_fetching_rtdb,false");
  delay(200);
}

void setSpotlight() {
  // digitalWrite(relaySpotlight, spotlightState ? LOW : HIGH);
  if (spotlightState) {
    digitalWrite(relaySpotlight, LOW);
  } else {
    digitalWrite(relaySpotlight, HIGH);
  }
  Serial.print(F("Spotlight State: "));
  Serial.println(spotlightState ? "ON" : "OFF");
}

void setLightpost() {
  if (lightpostState) {
    digitalWrite(relayLightPost, LOW);
  } else {
    digitalWrite(relayLightPost, HIGH);
  }
  Serial.print(F("Lightpost State: "));
  Serial.println(lightpostState ? "ON" : "OFF");
}

void setLaserLeft() {
  if (strcmp(leftLaserMode, "off") == 0) {
    lasers_left(false, false);
  } else if (strcmp(leftLaserMode, "steady") == 0) {
    lasers_left(true, true);
  } else if (leftLaserMode == "random") {
    // Random mode: relays turn on/off randomly within the interval
    if (currentMillis - leftLaserMillis >= randomDelay1) {
      leftLaserMillis = currentMillis;

      // Generate random on/off states for each relay
      bool relay1State = random(0, 2); // Random value 0 or 1
      bool relay2State = random(0, 2);

      Serial.println("led_pump_left: RANDOM STATE");
      lasers_left(relay1State, relay2State);
      randomDelay1 = random(3000, 5000);
    }
  } else {
    int currentInterval = strcmp(leftLaserMode, "slow") == 0 ? interval_5s : interval_3s;
    if (currentMillis - leftLaserMillis >= currentInterval) {
      leftLaserMillis = currentMillis;
      leftLaserState = !leftLaserState;
      lasers_left(!leftLaserState, !leftLaserState);
      Serial.println(F("Toggling left laser..."));
    }
  }
  Serial.print(F("Left Laser Mode: "));
  Serial.println(leftLaserMode);
}

void setLaserRight() {
  if (strcmp(rightLaserMode, "off") == 0) {
    lasers_right(false, false);
  } else if (strcmp(rightLaserMode, "steady") == 0) {
    lasers_right(true, true);
  } else if (rightLaserMode == "random") {
    // Random mode: relays turn on/off randomly within the interval
    if (currentMillis - rightLaserMillis >= randomDelay2) {
      rightLaserMillis = currentMillis;

      // Generate random on/off states for each relay
      bool relay1State = random(0, 2); // Random value 0 or 1
      bool relay2State = random(0, 2);

      Serial.println("led_pump_left: RANDOM STATE");
      lasers_right(relay1State, relay2State);
      randomDelay2 = random(3000, 5000);
    }
  } else {
    int currentInterval = strcmp(rightLaserMode, "slow") == 0 ? interval_5s : interval_3s;
    if (currentMillis - rightLaserMillis >= currentInterval) {
      rightLaserMillis = currentMillis;
      rightLaserState = !rightLaserState;
      lasers_right(!rightLaserState, !rightLaserState);
      Serial.println(F("Toggling right laser..."));
    }
  }
  Serial.print(F("Right Laser Mode: "));
  Serial.println(rightLaserMode);
}

void setup_firebase() {
  if (WiFi.status() == WL_CONNECTED) {
    config.api_key = API_KEY;
    config.database_url = DATABASE_URL;

    if (Firebase.signUp(&config, &auth, "", "")) {
      Serial.println(F("Firebase sign-up OK"));
      unoSerial.println("display_text,Firebase,Sign-Up OK");
      signupOK = true;
    } else {
      Serial.printf("Firebase sign-up failed: %s\n", config.signer.signupError.message.c_str());
      unoSerial.println("display_text,Firebase,Sign-Up FAILED");
    }

    config.token_status_callback = tokenStatusCallback;

    Firebase.begin(&config, &auth);

    Serial.println(F("Initializing Firebase streams..."));

    // Start listening to changes at the "deviceMode" path
    if (!Firebase.RTDB.beginStream(&fbdo1, "/up_diorama_01/custom_controls")) {
      Serial.println("Could not begin stream for custom_controls");
      Serial.println("REASON: " + fbdo1.errorReason());
    }

    // Start listening to changes at the "soundVolume" path
    if (!Firebase.RTDB.beginStream(&fbdo2, "/up_diorama_01/utilities")) {
      Serial.println("Could not begin stream for soundVolume");
      Serial.println("REASON: " + fbdo2.errorReason());
    }

    // Set stream callbacks for both paths
    Firebase.RTDB.setStreamCallback(&fbdo1, streamCallback1, streamTimeoutCallback);
    Firebase.RTDB.setStreamCallback(&fbdo2, streamCallback2, streamTimeoutCallback);
  }
}

void lasers_left(bool relay1State, bool relay2State) {
  digitalWrite(relayLaserLeft1, relay1State ? HIGH : LOW);
  digitalWrite(relayLaserLeft2, relay2State ? HIGH : LOW);
  // Serial.println(F("Left lasers state updated."));
}

void lasers_right(bool relay1State, bool relay2State) {
  digitalWrite(relayLaserRight1, relay1State ? HIGH : LOW);
  digitalWrite(relayLaserRight2, relay2State ? HIGH : LOW);
  // Serial.println(F("Right lasers state updated."));
}

void init_relay_pins() {
  pinMode(relayLaserLeft1, OUTPUT);
  pinMode(relayLaserLeft2, OUTPUT);
  pinMode(relayLaserRight1, OUTPUT);
  pinMode(relayLaserRight2, OUTPUT);
  pinMode(relaySpotlight, OUTPUT);
  pinMode(relayLightPost, OUTPUT);

  pinMode(VSENSOR_PIN, INPUT);
  pinMode(RESET_WIFI_PIN, INPUT_PULLUP);
}

void launch_wifi_config_portal() {
  unoSerial.println("display_text,Portal Launched,SSID:UP_Plaza_AP");
  //reset settings - for testing
  wm.resetSettings();

  // set configportal timeout
  wm.setConfigPortalTimeout(timeout);
  if (!wm.startConfigPortal("UP_Plaza_AP")) {
    Serial.println("failed to connect and hit timeout");
    unoSerial.println("display_text,FAILED,Portal CLosed");
    delay(3000);
    //reset and try again, or maybe put it to deep sleep
    ESP.restart();
    delay(5000);
  }
}

bool hasComma(String s) {
  return s.indexOf(',') >= 0;
}

String convertToKeyValue(String data, int index) {
  int found = 0;
  int strIndex[] = {0, -1};
  int maxIndex = data.length() - 1;

  for (int i = 0; i <= maxIndex && found <= index; i++) {
    if (data.charAt(i) == ',' || i == maxIndex) {
      found++;
      strIndex[0] = strIndex[1] + 1;
      strIndex[1] = (i == maxIndex) ? i + 1 : i;
    }
  }

  return found > index ? data.substring(strIndex[0], strIndex[1]) : "";
}

String removeNewlines(String str) {
  String result = "";
  for (int i = 0; i < str.length(); i++) {
    if (str[i] != '\n' && str[i] != '\r') {
      result += str[i];
    }
  }
  return result;
}
