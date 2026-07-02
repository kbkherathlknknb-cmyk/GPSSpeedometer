# 🏎️ Android GPS Speedometer & Automotive Telemetry Suite

![Android](https://img.shields.io/badge/Platform-Android_8.0%2B-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)
![Build](https://img.shields.io/badge/Build-Passing-brightgreen?style=for-the-badge)

A state-of-the-art Android navigation and performance telemetry application built with native **Kotlin**. Featuring an F1-style LED launch bar, automatic 0–50 km/h sprint timer, multi-mode gauge customization, picture-in-picture (PiP) & draggable floating overlay support, and an anti-jitter Doppler odometer.

---

## ✨ Key Features

### 🏎️ F1-Style Acceleration Telemetry & 0–50 km/h Sprint Timer
- **30-Segment +G Launch Bar**: Sweeps across the entire width of the dashboard, illuminating custom LED segments (from Cyan to Green, Yellow, and racing Red/Orange) as G-force increases toward **+1.2G Peak**.
- **⚡ Automatic 0–50 km/h (or 0–30 mph) Stopwatch**: Primes automatically when stationary (`READY`), triggers instantly upon launch detection (> 0.18G acceleration), and locks in your final sprint time with a trophy badge (`⚡ 0-50 km/h: 4.12s 🏆`).

### 🕹️ 4 Automotive Driving Modes
Access via the clean Three-Dot top menu (`⋮`) to transform color schemes and gauge needle responsiveness:
- **🧘 CALM**: Relaxed cyan glow with smooth, damped needle animations.
- **🌱 ECO**: Emerald green theme (`#00E676`) with lime accents, encouraging smooth acceleration.
- **🚗 TRAFFIC**: Amber gold styling (`#FFB300`), optimized for urban commuting.
- **🏁 AGGRESSIVE**: High-performance racing orange/red theme (`#FF3D00`) with hyper-responsive gauge updates (200ms duration) and highlighted telemetry peaks.

### 📱 Mini-Window Mode (Picture-in-Picture & Floating Overlay)
- **Picture-in-Picture (PiP)**: Seamlessly shrinks the speedometer into a system PiP window when multitasking on Android 8.0+.
- **Draggable Floating Overlay Service**: Launches an always-on-top floating speedometer widget over external navigation apps like **Google Maps** or **Waze**!

### 🎯 Anti-Jitter Odometer & Persistent Trip Log
- **Doppler & Accuracy Filtering**: Ignores stationary GPS noise (speeds < 0.8 m/s or accuracy > 20m), eliminating 100% of false mileage drift when stopped at traffic lights or indoors.
- **Total Lifetime Odometer**: Persistent cumulative mileage card tracked across app restarts.
- **JSON Trip History Log**: Records complete journey statistics (Date, Distance, Duration, Max Speed, and Avg Speed) to SharedPreferences with prompt-on-reset protection and clear log options.

### 🔄 Multi-Window Split-Screen & Orientation Rotation
- Natively supports orientation changes (Portrait & Landscape) and Android split-screen multitasking without losing active trip data or sprint timer state.
- Wrapped in a responsive `ScrollView` and dynamic layout containers to prevent clipping on compact displays.

---

## 🛠️ Technology Stack & Architecture

- **Language**: Kotlin (100%)
- **Location & Sensors**: Google Play Services Location (`FusedLocationProviderClient`), Android `SensorManager` (Accelerometer & Magnetic Field for compass heading and G-force calculations).
- **Custom Views & Canvas Drawing**:
  - `SpeedGaugeView.kt`: Custom circular speedometer gauge with dynamic arc rendering, unit switching (km/h vs mph), and mode-based damping.
  - `SportyAccelerationView.kt`: Custom F1 LED launch bar and real-time performance sprint stopwatch.
- **Background Services**: `FloatingSpeedometerService.kt` for draggable system alert window overlays.
- **Persistence**: SharedPreferences JSON serialization for trip history and total odometer persistence.

---

## 🚀 Getting Started & Building Locally

### Prerequisites
- **Android Studio**: Jellyfish / Koala (or newer)
- **JDK**: Java 17+
- **Android SDK**: API Level 34 (Minimum API 26 for PiP support)

### 1. Clone the Repository
```bash
git clone https://github.com/yourusername/gps-speedometer-android.git
cd gps-speedometer-android
```

### 2. Build the Debug APK
Using the included Gradle wrapper:
```bash
# On Windows (PowerShell or CMD)
.\gradlew.bat assembleDebug

# On macOS / Linux
./gradlew assembleDebug
```
The compiled APK will be output to:
`app/build/outputs/apk/debug/app-debug.apk`

### 3. Install on Device
Connect your Android device via USB debugging or Wireless ADB and run:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

---

## 📁 Project Structure

```
gps-speedometer-android/
├── app/src/main/
│   ├── java/com/gps/speedometer/
│   │   ├── MainActivity.kt               # Main controller, GPS callbacks & UI options
│   │   ├── SpeedGaugeView.kt             # Custom circular speedometer Canvas view
│   │   ├── SportyAccelerationView.kt     # F1 launch bar & 0-50 sprint timer Canvas view
│   │   ├── FloatingSpeedometerService.kt # Draggable floating overlay background service
│   │   └── SensorEngine.kt               # Accelerometer & Compass sensor fusion
│   ├── res/
│   │   ├── layout/activity_main.xml      # Responsive dashboard layout
│   │   ├── values/colors.xml             # Theme color palettes & mode tokens
│   │   └── drawable/                     # Custom vector assets & button backgrounds
│   └── AndroidManifest.xml               # PiP, Multi-window & Overlay permissions
├── build.gradle.kts                      # Root Gradle configuration
└── settings.gradle.kts                   # Module settings
```

---

## 📝 License
This project is licensed under the MIT License. Feel free to fork, modify, and use in your automotive or navigation projects!
