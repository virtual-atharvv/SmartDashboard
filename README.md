# ZASH — SmartDesk

An ESP32-powered smart desk companion that brings phone notifications, call alerts, music information, and clock status to a compact TFT display.

## 📂 Project Files & Resources

🔗 **[View ZASH Project Files on Google Drive](https://drive.google.com/file/d/1oOCCNiSRAja28u99D7z_4pqYuP5KZQ67/view?usp=sharing)**

## 🚀 Features

- 📱 Android notification display
- 📞 Incoming call alerts
- 🎵 Music information for Spotify and YouTube
- 🕒 Real-time clock and date
- 📶 Wi-Fi connectivity
- 🖥️ Compact, colorful TFT interface
- 🔌 ESP32-based embedded system

## 🛠️ Hardware

- ESP32 DevKit V1
- 1.8-inch ST7735 TFT LCD (160 × 128)
- Jumper wires and USB power

### TFT Pin Configuration

| TFT Pin | ESP32 GPIO |
|---|---|
| SCK | GPIO 18 |
| SDA / MOSI | GPIO 23 |
| RESET | GPIO 4 |
| CS | GPIO 5 |
| DC / A0 | GPIO 2 |
| VCC | 3.3V |
| GND | GND |

## 💻 Technology Stack

- Arduino C++
- ESP32 Wi-Fi
- Python and Flask middleware
- Android companion application
- ST7735 display library

## ⚙️ How It Works

The ESP32 drives the TFT display and connects over Wi-Fi. A Python/Flask middleware service can provide phone-related state to the device, while the Android companion application supports notification integration.

The display prioritizes incoming call alerts, notifications, music information, and the clock.

## 📁 Project Resources

For project files, supporting materials, and additional resources, visit the [Google Drive project folder](https://drive.google.com/file/d/1oOCCNiSRAja28u99D7z_4pqYuP5KZQ67/view?usp=sharing).

---

**ZASH — Your phone, reimagined on a tiny screen.**
