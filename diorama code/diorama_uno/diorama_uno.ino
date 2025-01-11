#include <SoftwareSerial.h>
#include<LiquidCrystal_I2C.h>
#include <DFRobotDFPlayerMini.h>

#define relayLeft1 4
#define relayLeft2 5
#define relayLeft3 6
#define relayLeft4 7
#define relayLeft5 8

#define relayRight1 9
#define relayRight2 10
#define relayRight3 11
#define relayRight4 12
#define relayRight5 13

#define RXp2 3
#define TXp2 2

#define PIN_MP3_TX A1
#define PIN_MP3_RX 0
#define DFPLAYER_BUSY_PIN 1

// Sensor pins
#define LDR_PIN A3
#define SOUND_SENSOR_PIN A4
#define WATER_LEVEL_PIN A5
#define VSENSOR_PIN  A2

#define MODE_BUTTON_PIN A0

// Interval constants
const uint16_t interval_5ns = 500;
const uint16_t interval_1s = 1000;
const uint16_t interval_2s = 2000;
const uint16_t interval_3s = 3000;
const uint16_t interval_5s = 5000;

const float correctionfactor = 6.4; 
const float R1 = 30000.0;
const float R2 = 7500.0;

const char* seasons[] = {"ambient", "night", "christmas", "sentry", "party"};
int currentSeasonIndex = 0;

float vout = 0.0; 
float vin = 0.0; 

int randomDelay1 = interval_3s;
int randomDelay2 = interval_3s;

// Time delays
unsigned long currentMillis = 0;
unsigned long leftLedPumpMillis = 0;
unsigned long rightLedPumpMillis = 0;
unsigned long updateSensorMillis = 0;
unsigned long lcdDisplayMillis = 0;
unsigned long soundEventMillis = 0;
unsigned long modeButtonEventMillis = 0;

// Relay connected components states
bool leftLedPumpState = false;
bool rightLedPumpState = false;
bool lightpostState = false;
bool spotlightState = false;

String deviceMode = "ambient"; //ambient

String leftLedPumpMode = "";
String rightLedPumpMode = "";
String leftLaserMode = "";
String rightLaserMode = "";
int musicVolume = 30;
int musicNum = 1;
// lightpost and spotlight is on/off only

String prevDeviceMode = "";
int prevMusicNum = 0;
String prevLeftLaserMode = "";
String prevRightLaserMode = "";
bool prevLightpostState;
bool prevSpotlightState;

bool isFetchingRtdb = false;

String lcdText = "";
String prevLcdText = "";
bool isTextDisplayed = false;

String powerSource = "";
String prevPowerSource = "n";

SoftwareSerial espSerial(RXp2, TXp2); // RX, TX for ESP communication
SoftwareSerial playerSerial(PIN_MP3_RX, PIN_MP3_TX);

DFRobotDFPlayerMini player;
LiquidCrystal_I2C lcd(0x27,16,2);

void setup() {
  // Serial.begin(9600);
  espSerial.begin(19200);
  playerSerial.begin(9600);
  lcd.init();

  pinMode(MODE_BUTTON_PIN, INPUT_PULLUP);
  pinMode(DFPLAYER_BUSY_PIN, INPUT);
  pinMode(LDR_PIN, INPUT);
  pinMode(SOUND_SENSOR_PIN, INPUT);
  pinMode(WATER_LEVEL_PIN, INPUT);
  pinMode(VSENSOR_PIN, INPUT);

  // Relay initialization
  init_relay_pins();

  delay(2000);
  initMp3Player();

  lcdText = "INITIALIZATION,COMPLETE";
  delay(1500);

  lcd.backlight();
  // espSerial.println("Arduino Initialized");
}

// ###########################  MAIN LOOP ##############################################
void loop() {
  currentMillis = millis();
  bool modeReading = digitalRead(MODE_BUTTON_PIN);

  // if (Serial.available() > 0) {
  //   String receivedChar = Serial.readStringUntil('\n');
  //   // Serial.print("Serial sent: ");
  //   // Serial.println(receivedChar);
  //   espSerial.print("Serial sent: ");
  //   espSerial.println(receivedChar);
  // }

  if (!espSerial.isListening()) {
    espSerial.listen();
    delay(500);
  }

  // Check if data is available from espSerial
  if (espSerial.available() > 0) {
    String val = espSerial.readStringUntil('\n');
    val = removeNewlines(val);
    if (val.length() != 0) {
      if (hasComma(val)) {
        // Serial.println("HAS COMMA");
        // espSerial.println("HAS COMMA");
        String key = convertToKeyValue(val, 0);
        String keyValue = convertToKeyValue(val, 1);

        if (key == "device_mode") {
          deviceMode = keyValue;
        } else if (key == "music_name") {
          setMusic(keyValue);
        }  else if (key == "sound_volume") {
          musicVolume = keyValue.toInt();
          player.volume(musicVolume);
        } else if (key == "left_led_pump_mode") {
          leftLedPumpMode = keyValue;
        } else if (key == "right_led_pump_mode") {
          rightLedPumpMode = keyValue;
        } else if (key == "left_laser_mode") {
          leftLaserMode = keyValue;
        } else if (key == "right_laser_mode") {
          rightLaserMode = keyValue;
        } else if (key == "lightpost_state") {
          lightpostState = keyValue.equals("true");
        } else if (key == "spotlight_state") {
          spotlightState = keyValue.equals("true");
        } else if (key == "display_text") {
          String keyValue2 = convertToKeyValue(val, 2);
          lcdText = keyValue + "," + keyValue2;
        } else if (key == "is_fetching_rtdb") {
          isFetchingRtdb = keyValue.equals("true");
        }
      } else {
        // Serial.println("NOT HAVE COMMA"); 
        // espSerial.println("NOT HAVE COMMA");
      }
    }
  }

  // Function to check if the button was pressed with debounce
  if (modeReading == LOW) {
    espSerial.println("MODE BUTTON IS LOW");
    if (millis() - modeButtonEventMillis > 1000) {
      espSerial.println("Button Pressed: MODE CHANGED!");
      currentSeasonIndex = (currentSeasonIndex + 1) % 5;
      deviceMode = seasons[currentSeasonIndex];
    }
  
    // Remember when last event happened
    modeButtonEventMillis = millis();
  } else {
    // espSerial.println("MODE BUTTON IS HIGH");
  }

  // Handles LCD display
  if (lcdText != prevLcdText) {
    if (!isTextDisplayed) {
      lcd.clear();
      if (hasComma(lcdText)) {
        int commaIndex = lcdText.indexOf(',');
        if (commaIndex > 0 && commaIndex < lcdText.length() - 1) {
          String firstRow = lcdText.substring(0, commaIndex);
          String secondRow = lcdText.substring(commaIndex + 1);

          // Display the two rows of text
          lcd.setCursor(0, 0);
          lcd.print(firstRow);
          lcd.setCursor(0, 1);
          lcd.print(secondRow);
        } else {
          lcd.setCursor(0, 0);
          lcd.print(lcdText);
        }
      } else {
        lcd.setCursor(0, 0);
        lcd.print(lcdText);
      }
      
      isTextDisplayed = true;
      lcdDisplayMillis = currentMillis;
    }

    if (currentMillis - lcdDisplayMillis >= interval_2s) {
      lcdDisplayMillis = currentMillis;
      // Serial.println("lcdDisplayMillis END");
      // espSerial.println("lcdDisplayMillis END");
      String vs = "MODE: " + deviceMode;

      lcd.clear();
      lcd.setCursor(0, 0);
      lcd.print(" Oblation Plaza ");
      lcd.setCursor(0, 1);
      lcd.print(vs);

      isTextDisplayed = false;
      prevLcdText = lcdText;
    }
  }

  // String dev = "DEVICE_MODE: " + deviceMode;
  // espSerial.println(dev);
  if (!isFetchingRtdb) {
    if (deviceMode == "custom") {
      if (deviceMode != prevDeviceMode) {
        lcdText = "Fetching Data...,PLEASE WAIT";
        espSerial.println("is_fetching_rtdb,true");
        isFetchingRtdb = true;
      }
    }
    else if (deviceMode == "ambient") {
      int ldr_val = digitalRead(LDR_PIN);

      if (ldr_val == LOW) {
        // DAY
        if (musicNum != 1) {
          musicNum = 1;
        }

        leftLedPumpMode = "steady";
        rightLedPumpMode = "steady";
        leftLaserMode = "off";
        rightLaserMode = "off";
        lightpostState = false;
        spotlightState = false;
      } else {
        // NIGHT
        if (musicNum != 4) {
          musicNum = 4;
        }

        leftLedPumpMode = "slow";
        rightLedPumpMode = "slow";
        leftLaserMode = "off";
        rightLaserMode = "off";
        lightpostState = true;
        spotlightState = true;
      }
    }
    else if (deviceMode == "party") {
      int sound_val = digitalRead(SOUND_SENSOR_PIN);
      int ldr_val = digitalRead(LDR_PIN);

      leftLedPumpMode = "random";
      rightLedPumpMode = "random";

      if (musicNum != 9) {
        musicNum = 9;
      }

      if (ldr_val == LOW) {
        lightpostState = false;
      } else {
        lightpostState = true;
        // espSerial.println("sound_val: LOWWWWWWW");
      }

      // If pin goes HIGH, sound is detected
      if (sound_val == LOW) {
        // Serial.println("SENSOR DATA HIGH");
        // If 25ms have passed since last HIGH state, it means that
        // the clap is detected and not due to any spurious sounds
        if (millis() - soundEventMillis > 25) {
          // Serial.print("Sound detected!");
          // espSerial.println("Sound detected!");
          leftLaserMode = "steady";
          rightLaserMode = "steady";
          spotlightState = true;
        }
      
        // Remember when last event happened
        soundEventMillis = millis();

        // no music
      } else {
        leftLaserMode = "off";
        rightLaserMode = "off";
        spotlightState = false;
      }
    } 
    else if (deviceMode == "sentry") {
      int sound_val = digitalRead(SOUND_SENSOR_PIN);
      int ldr_val = digitalRead(LDR_PIN);

      leftLedPumpMode = "steady";
      rightLedPumpMode = "steady";

      if (musicNum != 0) {
        musicNum = 0;
      }

      if (ldr_val == LOW) {
        lightpostState = false;
      } else {
        lightpostState = true;
        // espSerial.println("sound_val: LOWWWWWWW");
      }

      // If pin goes HIGH, sound is detected
      if (sound_val == LOW) {
        // Serial.println("SENSOR DATA HIGH");
        // If 25ms have passed since last HIGH state, it means that
        // the clap is detected and not due to any spurious sounds
        if (millis() - soundEventMillis > 25) {
          // Serial.print("Sound detected!");
          // espSerial.println("Sound detected!");
          leftLaserMode = "steady";
          rightLaserMode = "steady";
          spotlightState = true;
        }
      
        // Remember when last event happened
        soundEventMillis = millis();

        // no music
      } else {
        leftLaserMode = "off";
        rightLaserMode = "off";
        spotlightState = false;
      }
    } else if (deviceMode == "christmas") {
      int ldr_val = digitalRead(LDR_PIN);

      if (ldr_val == LOW) {
        if (musicNum != 6) {
        musicNum = 6;
        }

        leftLedPumpMode = "steady";
        rightLedPumpMode = "steady";
        leftLaserMode = "off";
        rightLaserMode = "off";
        lightpostState = false;
        spotlightState = false;
      } else {
        // NIGHT
        if (musicNum != 2) {
        musicNum = 2;
        }

        leftLedPumpMode = "slow";
        rightLedPumpMode = "slow";
        leftLaserMode = "steady";
        rightLaserMode = "steady";
        lightpostState = true;
        spotlightState = true;
      }
      
    }
    else if (deviceMode == "night") {
      if (musicNum != 4) {
        musicNum = 4;
      }

      leftLedPumpMode = "slow";
      rightLedPumpMode = "slow";
      leftLaserMode = "off";
      rightLaserMode = "off";
      lightpostState = true;
      spotlightState = true;
    }

    // handles playing of music
    if (musicNum != 0) {
      if (musicNum != prevMusicNum) {
        lcdText = "NEW MUSIC SET";
        player.play(musicNum);
        prevMusicNum = musicNum;
      } else {
        if (!isMusicPlaying()) {
          lcdText = "MUSIC REPEATED";
          player.play(musicNum);
        }
      }
    } else {
      if (isMusicPlaying()) {
        player.stop();
      }
    }

    setLedPumpLeft();
    setLedPumpRight();
    setLaserLeft();
    setLaserRight();
    setLightpost();
    setSpotlight();
  }

  if (deviceMode != prevDeviceMode) {
    lcdText = "Mode Changed!," + deviceMode;
    prevDeviceMode = deviceMode;

    if (deviceMode != "custom") {
      isFetchingRtdb = false;
    }
  }

  if (powerSource != prevPowerSource) {
    String toSend = "power_source," + powerSource;
    espSerial.println(toSend);
    prevPowerSource = powerSource;
  } else {
    // Voltage sensor
    String powerSource = "";
    int vdata = analogRead(VSENSOR_PIN);
    vout = (vdata * 5.0) / 1023.0;
    vin = vout / (R2/(R1+R2));

    vin = vin - correctionfactor;

    if (vin <= 0) {
      powerSource = "battery";
    } else {
      powerSource = "adapter";
    }
  }

  if (currentMillis - updateSensorMillis >= 60000) {
    updateSensorMillis = currentMillis;
    updateWaterLevel();
  }
}
// ###########################  MAIN LOOP END ##############################################

void updateWaterLevel() {
  int data = analogRead(WATER_LEVEL_PIN);
  String waterLevel = "";
  if (data <= 300) {
    waterLevel = "low";
  } else if (data > 300 && data < 600) {
    waterLevel = "normal";
  } else if (data >= 600) {
    waterLevel = "high";
  }

  String toSend = "water_level,"  + waterLevel;
  espSerial.println(toSend);
}

void setMusic(String musicName) {
  // 0001 - UP Naming Mahal
  // 0002 - Christmas Mashup
  // 0003 - Party Mashup
  // 0004 - Ambient
  // 0005 - Bass Boosted
  // 0006 - Jose Mari Chan Mashup
  // 0007 - Nokia
  // 0008 - Intro
  // 0009 - APT

  if (musicName.equalsIgnoreCase("up hymn")) {
    musicNum = 1;
  } else if (musicName.equalsIgnoreCase("christmas mashup")) {
    musicNum = 2;
  } else if (musicName.equalsIgnoreCase("party mashup")) {
    musicNum = 3;
  } else if (musicName.equalsIgnoreCase("ambient")) {
    musicNum = 4;
  } else if (musicName.equalsIgnoreCase("bass boosted")) {
    musicNum = 5;
  } else if (musicName.equalsIgnoreCase("jose mari chan mashup")) {
    musicNum = 6;
  } else if (musicName.equalsIgnoreCase("apt")) {
    musicNum = 9;
  } else if (musicName.equalsIgnoreCase("none")) {
    musicNum = 0;
  } else {
    Serial.print("NO MATCH MUSIC NAME");
  }
}

void setLightpost() {
  if (lightpostState != prevLightpostState) {
    String res = lightpostState ? "true" : "false";
    String toSend = "lightpost_state," + res;
    espSerial.println(toSend);
    prevLightpostState = lightpostState;
  } 
}

void setSpotlight() {
  if (spotlightState != prevSpotlightState) {
    String res = spotlightState ? "true" : "false";
    String toSend = "spotlight_state," + res;
    espSerial.println(toSend);
    prevSpotlightState = spotlightState;
  }
}

void setLaserLeft() {
  if (leftLaserMode != prevLeftLaserMode) {
    String toSend = "left_laser_mode," + leftLaserMode;
    espSerial.println(toSend);
    prevLeftLaserMode = leftLaserMode;
  }
}

void setLaserRight() {
  if (rightLaserMode != prevRightLaserMode) {
    String toSend = "right_laser_mode," + rightLaserMode;
    espSerial.println(toSend);
    prevRightLaserMode = rightLaserMode;
  }
}

void setLedPumpRight() {
  // Right pool pump and led
  if (rightLedPumpMode == "off") {
    led_pump_right(true, true, true, true, true);
  } else if (rightLedPumpMode == "steady") {
    // Serial.println("custom Mode: steady");
    led_pump_right(false, false, false, false, false);
  } else if (rightLedPumpMode == "random") {
    // Random mode: relays turn on/off randomly within the interval
    if (currentMillis - rightLedPumpMillis >= randomDelay2) {
      rightLedPumpMillis = currentMillis;

      // Generate random on/off states for each relay
      bool relay1State = random(0, 2); // Random value 0 or 1
      bool relay2State = random(0, 2);
      bool relay3State = random(0, 2);
      bool relay4State = random(0, 2);
      bool relay5State = random(0, 2);

      // Serial.println("led_pump_right: RANDOM STATE");
      // espSerial.println("led_pump_right: RANDOM STATE");
      led_pump_right(relay1State, relay2State, relay3State, relay4State, relay5State);
      randomDelay2 = random(3000, 5000);
    }
  } else {
    int currentInterval = 0;
    if (rightLedPumpMode == "slow") {
      currentInterval = interval_5s;
    } else if (rightLedPumpMode == "fast") {
      currentInterval = interval_3s;
    }

    if (currentMillis - rightLedPumpMillis >= currentInterval) {
      rightLedPumpMillis = currentMillis;

      rightLedPumpState = !rightLedPumpState;

      if (rightLedPumpState) {
        // Serial.println("led_pump_right: ON");
        led_pump_right(false, false, false, false, false);
      } else {
        // Serial.println("led_pump_right: OFF");
        led_pump_right(true, true, true, true, true);
      }
    }
  }
}

void setLedPumpLeft() {
  // Left water and pump led
  if (leftLedPumpMode == "off") {
    led_pump_left(true, true, true, true, true);
  } else if (leftLedPumpMode == "steady") {
    // "custom Mode: steady");
    led_pump_left(false, false, false, false, false);
  } else if (leftLedPumpMode == "random") {
    // Random mode: relays turn on/off randomly within the interval
    if (currentMillis - leftLedPumpMillis >= randomDelay1) {
      leftLedPumpMillis = currentMillis;

      // Generate random on/off states for each relay
      bool relay1State = random(0, 2); // Random value 0 or 1
      bool relay2State = random(0, 2);
      bool relay3State = random(0, 2);
      bool relay4State = random(0, 2);
      bool relay5State = random(0, 2);

      // Serial.println("led_pump_left: RANDOM STATE");
      espSerial.println("led_pump_left: RANDOM STATE");
      led_pump_left(relay1State, relay2State, relay3State, relay4State, relay5State);
      randomDelay1 = random(3000, 5000);
    }
  } else {
    int currentInterval = 0;
    if (leftLedPumpMode == "slow") {
      currentInterval = interval_5s;
    } else if (leftLedPumpMode == "fast") {
      currentInterval = interval_3s;
    }

    if (currentMillis - leftLedPumpMillis >= currentInterval) {
      leftLedPumpMillis = currentMillis;

      leftLedPumpState = !leftLedPumpState;

      if (leftLedPumpState) {
        // Serial.println("led_pump_left: ON");
        // espSerial.println("led_pump_left: ON");
        led_pump_left(false, false, false, false, false);
      } else {
        // Serial.println("led_pump_left: OFF");
        // espSerial.println("led_pump_left: OFF");
        led_pump_left(true, true, true, true, true);
      }
    }
  }
}

void led_pump_left(bool relay1State, bool relay2State, bool relay3State, bool relay4State, bool relay5State) {
  // Control each relay based on the passed parameters
  digitalWrite(relayLeft1, relay1State ? HIGH : LOW);
  digitalWrite(relayLeft2, relay2State ? HIGH : LOW);
  digitalWrite(relayLeft3, relay3State ? HIGH : LOW);
  digitalWrite(relayLeft4, relay4State ? HIGH : LOW);
  digitalWrite(relayLeft5, relay5State ? HIGH : LOW);
}

void led_pump_right(bool relay1State, bool relay2State, bool relay3State, bool relay4State, bool relay5State) {
  // Control each relay based on the passed parameters
  digitalWrite(relayRight1, relay1State ? HIGH : LOW);
  digitalWrite(relayRight2, relay2State ? HIGH : LOW);
  digitalWrite(relayRight3, relay3State ? HIGH : LOW);
  digitalWrite(relayRight4, relay4State ? HIGH : LOW);
  digitalWrite(relayRight5, relay5State ? HIGH : LOW);
}

bool isMusicPlaying() {
  bool playerStatus = digitalRead(DFPLAYER_BUSY_PIN) == LOW;
  return playerStatus;
}

void initMp3Player() {
  if (player.begin(playerSerial, false, false)) {
    // Serial.println("Mp3Player OK");
    // espSerial.println("Mp3Player OK");
    // Set volume to maximum (0 to 30)
    player.volume(30);
    // Play the first track and set isPlaying flag to true
    player.play(8);
  } else {
    // Serial.println("Connecting to DFPlayer Mini failed!");
    // espSerial.println("Connecting to DFPlayer Mini failed!");
  }
  delay(1500);
}

String convertToKeyValue(String strData, int index) {
  String listData[3];

  int startPos = 0;
  int separatorPos;
  int currentIndex = 0;

  while ((separatorPos = strData.indexOf(',', startPos)) != -1 && currentIndex < 2) {
    listData[currentIndex] = strData.substring(startPos, separatorPos);
    startPos = separatorPos + 1;
    currentIndex++;
  }

  listData[currentIndex] = strData.substring(startPos);

  if (index >= 0 && index < 3) {
    return listData[index];
  } else {
    return "";
  }
}

bool hasComma(const String& str) {
  const char* str_char_array = str.c_str();
  return strchr(str_char_array, ',') != nullptr;
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

void init_relay_pins() {
  pinMode(relayLeft1, OUTPUT);
  pinMode(relayLeft2, OUTPUT);
  pinMode(relayLeft3, OUTPUT);
  pinMode(relayLeft4, OUTPUT);
  pinMode(relayLeft5, OUTPUT);

  pinMode(relayRight1, OUTPUT);
  pinMode(relayRight2, OUTPUT);
  pinMode(relayRight3, OUTPUT);
  pinMode(relayRight4, OUTPUT);
  pinMode(relayRight5, OUTPUT);
}
