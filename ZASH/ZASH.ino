
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h>
#include <SPI.h>
#include <Adafruit_GFX.h>
#include <Adafruit_ST7735.h>
#include <time.h>
#include <sys/time.h>

// ---------- WIFI SETTINGS ----------
const char* WIFI_SSID = "virtual-atharvv 5GHz";
const char* WIFI_PASSWORD = "Athrvv@2910";

const char* SERVER_URL =
  "http://10.24.63.141:5000/api/state";

// ---------- TFT WIRING ----------
#define TFT_SCLK 18
#define TFT_MOSI 23
#define TFT_RST   4
#define TFT_CS    5
#define TFT_DC    2

Adafruit_ST7735 tft(TFT_CS, TFT_DC, TFT_RST);

// ---------- NEXUS THEME ----------
const uint16_t BG     = 0x0841;
const uint16_t PANEL  = 0x10A4;
const uint16_t CYAN   = 0x07FF;
const uint16_t TEAL   = 0x05D7;
const uint16_t WHITE  = 0xFFFF;
const uint16_t MUTED  = 0x9D5B;
const uint16_t YELLOW = 0xFFE0;
const uint16_t RED    = 0xF800;
const uint16_t BLUE   = 0x867F;
const uint16_t LINE   = 0x2149;

// ---------- DASHBOARD STATE ----------
String currentScreen = "";
String lastSignature = "";

String musicTitle = "";
String musicArtist = "";
String musicSource = "";

String caller = "";
String callStatus = "ended";

String notificationApp = "";
String notificationSender = "";
String notificationText = "";

unsigned long lastPoll = 0;
unsigned long lastClockDraw = 0;
unsigned long lastWifiAttempt = 0;

const unsigned long POLL_INTERVAL = 500;
const unsigned long CLOCK_INTERVAL = 1000;

bool serverTimeReceived = false;
bool clockLayoutDrawn = false;
bool waitingLayoutDrawn = false;

String lastTimeShown = "";
String lastDateShown = "";
String lastClockStatus = "";

// ---------- TEXT HELPERS ----------
String cleanText(String value) {
  value.replace("\n", " ");
  value.replace("\r", " ");
  value.trim();
  return value;
}

String shortText(String value, int maxLen) {
  value = cleanText(value);

  if ((int)value.length() > maxLen) {
    if (maxLen <= 3) return value.substring(0, maxLen);
    value = value.substring(0, maxLen - 3) + "...";
  }

  return value;
}

void textAt(const String& text, int x, int y,
            uint16_t color, uint8_t size = 1) {
  tft.setTextWrap(false);
  tft.setTextSize(size);
  tft.setTextColor(color);
  tft.setCursor(x, y);
  tft.print(text);
}

void centerText(const String& text, int y,
                uint16_t color, uint8_t size = 1) {
  tft.setTextWrap(false);
  tft.setTextSize(size);
  tft.setTextColor(color);

  int16_t x1, y1;
  uint16_t w, h;

  tft.getTextBounds(text, 0, y, &x1, &y1, &w, &h);

  int x = (160 - (int)w) / 2;
  if (x < 2) x = 2;

  tft.setCursor(x, y);
  tft.print(text);
}

// ---------- COMMON FRAME ----------
void drawBase(const String& label, uint16_t accent) {
  tft.fillScreen(BG);

  tft.fillRect(0, 0, 160, 19, PANEL);
  tft.drawFastHLine(0, 19, 160, accent);
  tft.drawFastHLine(5, 21, 150, LINE);

  tft.fillRect(5, 5, 3, 9, accent);
  tft.fillRect(10, 5, 3, 9, CYAN);

  textAt("ZASH", 17, 6, WHITE);
  textAt("/", 57, 6, MUTED);
  textAt(label, 65, 6, accent);

  uint16_t statusColor =
    WiFi.status() == WL_CONNECTED ? TEAL : RED;

  tft.fillCircle(144, 9, 2, statusColor);
}

void drawFooter() {
  tft.drawFastHLine(5, 113, 150, LINE);

  textAt(WiFi.status() == WL_CONNECTED ? "WIFI OK" : "WIFI OFF",
         6, 118,
         WiFi.status() == WL_CONNECTED ? TEAL : RED);

  textAt("@athrvv v1", 98, 118, CYAN);
}

void drawCard(int x, int y, int w, int h,
              uint16_t accent) {
  tft.fillRoundRect(x, y, w, h, 6, PANEL);
  tft.drawRoundRect(x, y, w, h, 6, LINE);
  tft.drawFastVLine(x + 2, y + 6, h - 12, accent);
}

// ---------- CLOCK: LOW-FLICKER ----------
void drawClock() {
  struct tm ti;

  if (!getLocalTime(&ti, 10)) {
    if (!waitingLayoutDrawn) {
      drawBase("SYSTEM", CYAN);
      centerText("WAITING FOR TIME", 45, WHITE);
      centerText("Syncing server...", 66, MUTED);
      drawFooter();

      waitingLayoutDrawn = true;
      clockLayoutDrawn = false;
    }
    return;
  }

  waitingLayoutDrawn = false;

  char timeBuffer[10];
  char dateBuffer[20];

  strftime(timeBuffer, sizeof(timeBuffer), "%H:%M", &ti);
  strftime(dateBuffer, sizeof(dateBuffer), "%a, %d %b", &ti);

  String newTime = String(timeBuffer);
  String newDate = String(dateBuffer);
  String newStatus =
    WiFi.status() == WL_CONNECTED ? "ONLINE" : "OFFLINE";

  if (!clockLayoutDrawn) {
    drawBase("SYSTEM CORE", CYAN);

    textAt("LOCAL TIME", 9, 29, MUTED);

    tft.drawFastHLine(22, 76, 116, LINE);
    tft.fillRoundRect(65, 75, 30, 3, 1, CYAN);

    drawCard(8, 84, 144, 24, TEAL);
    textAt("SYSTEM STATUS", 15, 89, MUTED);

    drawFooter();

    clockLayoutDrawn = true;
    lastTimeShown = "";
    lastDateShown = "";
    lastClockStatus = "";
  }

  if (newDate != lastDateShown) {
    tft.fillRect(80, 27, 75, 13, BG);
    textAt(newDate, 83, 29, CYAN);
    lastDateShown = newDate;
  }

  if (newTime != lastTimeShown) {
    tft.fillRect(10, 39, 140, 36, BG);
    centerText(newTime, 43, WHITE, 3);
    lastTimeShown = newTime;
  }

  if (newStatus != lastClockStatus) {
    tft.fillRect(70, 97, 75, 11, PANEL);
    textAt(newStatus, 75, 99,
           WiFi.status() == WL_CONNECTED ? TEAL : RED);

    lastClockStatus = newStatus;
  }
}

// ---------- MUSIC ----------
void drawMusic() {
  drawBase("NOW PLAYING", TEAL);
  drawCard(7, 26, 146, 80, TEAL);

  tft.drawCircle(24, 42, 8, CYAN);
  tft.fillCircle(21, 45, 2, CYAN);
  tft.fillCircle(27, 43, 2, CYAN);

  textAt("NOW PLAYING", 37, 34, CYAN);
  textAt(shortText(musicTitle, 19), 14, 55, WHITE);
  textAt(shortText(musicArtist, 22), 14, 69, MUTED);
  textAt(shortText(musicSource, 20), 14, 83, YELLOW);

  tft.fillRoundRect(14, 96, 130, 4, 2, LINE);
  tft.fillRoundRect(14, 96, 54, 4, 2, CYAN);

  drawFooter();
}

// ---------- NOTIFICATION: TEXT ONLY ----------
void drawNotification() {
  drawBase("MESSAGE", YELLOW);
  drawCard(7, 26, 146, 81, YELLOW);

  // No app icon; show app name as text.
  textAt(shortText(notificationApp, 22), 14, 34, YELLOW);
  tft.drawFastHLine(14, 55, 130, LINE);

  textAt(shortText(notificationSender, 22), 14, 61, WHITE);

  String msg = cleanText(notificationText);
  const int charsPerLine = 23;
  int y = 76;

  while (msg.length() > 0 && y <= 99) {
    int take = min(charsPerLine, (int)msg.length());
    int cut = take;

    if (take < (int)msg.length()) {
      int space = msg.lastIndexOf(' ', take);
      if (space > 0) cut = space;
    }

    String line = msg.substring(0, cut);
    line.trim();

    textAt(line, 14, y, CYAN);

    msg.remove(0, cut);
    msg.trim();
    y += 10;
  }

  drawFooter();
}

// ---------- CALL ----------
void drawCall() {
  drawBase("PHONE CALL", RED);
  drawCard(7, 29, 146, 74, RED);

  centerText("INCOMING CALL", 38, RED);
  centerText(shortText(caller, 22), 56, WHITE);

  if (callStatus == "ringing") {
    centerText("RINGING...", 76, YELLOW);
  } else {
    centerText("CALL IN PROGRESS", 76, TEAL);
  }

  drawFooter();
}

// ---------- OFFLINE ----------
void drawOffline() {
  drawBase("SYSTEM", RED);

  centerText("SERVER OFFLINE", 42, RED);
  centerText("Check Wi-Fi", 61, WHITE);
  centerText("and laptop server", 76, MUTED);

  drawFooter();
}

// ---------- SCREEN ROUTER ----------
void drawCurrentScreen() {
  clockLayoutDrawn = false;
  waitingLayoutDrawn = false;

  lastTimeShown = "";
  lastDateShown = "";
  lastClockStatus = "";

  if (currentScreen == "call") {
    drawCall();
  } else if (currentScreen == "notification") {
    drawNotification();
  } else if (currentScreen == "music") {
    drawMusic();
  } else {
    drawClock();
  }
}

// ---------- WI-FI ----------
void connectWiFi() {
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  tft.fillScreen(BG);
  centerText("ZASH // NEXUS", 31, CYAN);
  centerText("Connecting Wi-Fi", 55, WHITE);
  centerText("Please wait...", 72, MUTED);

  unsigned long start = millis();

  while (WiFi.status() != WL_CONNECTED &&
         millis() - start < 20000) {
    delay(250);
  }

  if (WiFi.status() == WL_CONNECTED) {
    tft.fillScreen(BG);
    centerText("WIFI CONNECTED", 39, TEAL);
    centerText(WiFi.localIP().toString(), 59, WHITE);
    centerText("@athrvv v2.0", 79, CYAN);
    delay(1000);
  } else {
    drawOffline();
  }
}

// ---------- SERVER API ----------
bool fetchDashboard() {
  if (WiFi.status() != WL_CONNECTED) return false;

  HTTPClient http;
  http.setConnectTimeout(1500);
  http.setTimeout(1500);
  http.begin(SERVER_URL);

  int code = http.GET();

  if (code != HTTP_CODE_OK) {
    http.end();
    return false;
  }

  String payload = http.getString();
  http.end();

  DynamicJsonDocument doc(4096);
  DeserializationError error = deserializeJson(doc, payload);

  if (error) return false;

  String newScreen = doc["screen"] | "clock";

  JsonObject music = doc["music"];
  JsonObject call = doc["call"];
  JsonObject notification = doc["notification"];

  String newTitle = music["title"] | "";
  String newArtist = music["artist"] | "";
  String newSource = music["source"] | "";

  String newCaller = call["caller"] | "";
  String newCallStatus = call["status"] | "ended";

  String newApp = notification["app"] | "";
  String newSender = notification["sender"] | "";
  String newText = notification["text"] | "";

  // Sync server time, correcting drift only when needed.
  time_t epoch = doc["time"] | 0;

  if (epoch > 1700000000) {
    time_t localEpoch = time(nullptr);

    if (!serverTimeReceived ||
        labs((long)(epoch - localEpoch)) > 3) {
      struct timeval tv;
      tv.tv_sec = epoch;
      tv.tv_usec = 0;
      settimeofday(&tv, nullptr);
    }

    serverTimeReceived = true;
  }

  String signature =
    newScreen + "|" + newTitle + "|" + newArtist + "|" +
    newSource + "|" + newCaller + "|" + newCallStatus +
    "|" + newApp + "|" + newSender + "|" + newText;

  bool changed = signature != lastSignature;

  currentScreen = newScreen;
  musicTitle = newTitle;
  musicArtist = newArtist;
  musicSource = newSource;

  caller = newCaller;
  callStatus = newCallStatus;

  notificationApp = newApp;
  notificationSender = newSender;
  notificationText = newText;

  if (changed) {
    lastSignature = signature;
    drawCurrentScreen();
    lastClockDraw = millis();
  }

  return true;
}

// ---------- SETUP ----------
void setup() {
  Serial.begin(115200);

  SPI.begin(TFT_SCLK, -1, TFT_MOSI, TFT_CS);

  tft.initR(INITR_BLACKTAB);
  tft.setRotation(1);
  tft.fillScreen(BG);

  // India Standard Time
  setenv("TZ", "IST-5:30", 1);
  tzset();

  connectWiFi();

  if (WiFi.status() == WL_CONNECTED) {
    Serial.print("ESP32 IP: ");
    Serial.println(WiFi.localIP());

    Serial.print("Dashboard server: ");
    Serial.println(SERVER_URL);
  }

  currentScreen = "clock";
  drawCurrentScreen();
}

// ---------- MAIN LOOP ----------
void loop() {
  unsigned long now = millis();

  if (WiFi.status() != WL_CONNECTED &&
      now - lastWifiAttempt >= 5000) {
    lastWifiAttempt = now;
    WiFi.reconnect();
  }

  if (now - lastPoll >= POLL_INTERVAL) {
    lastPoll = now;

    bool ok = fetchDashboard();

    if (!ok && WiFi.status() == WL_CONNECTED) {
      Serial.println("Dashboard server request failed");
    }
  }

  if (currentScreen == "clock" &&
      now - lastClockDraw >= CLOCK_INTERVAL) {
    lastClockDraw = now;
    drawClock();
  }

  delay(5);
}
