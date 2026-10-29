# 📱 Proximity Controller (Ultra-Lightweight)

<p align="center">
  <img src="https://img.shields.io/badge/APK%20Size-23.6%20KB-success?style=for-the-badge&logo=android" alt="APK Size" />
  <img src="https://img.shields.io/badge/RAM%20Footprint-%3C%203%20MB-blue?style=for-the-badge&logo=speedtest" alt="RAM Footprint" />
  <img src="https://img.shields.io/badge/Target-Android%207.1.2%20(API%2025)-orange?style=for-the-badge&logo=android" alt="Target SDK" />
  <img src="https://img.shields.io/badge/GApps-None%20(Zero%20Dependency)-red?style=for-the-badge" alt="GApps Dependency" />
  <img src="https://img.shields.io/badge/License-MIT-green?style=for-the-badge" alt="License" />
</p>

<p align="center">
  <strong>An ultra-lean, battery-friendly, zero-allocation Android proximity utility engineered for legacy hardware and minimalist custom ROMs (LineageOS / AOSP).</strong>
</p>

<p align="center">
  <a href="https://github.com/ArtixForLiving/Proximity-Controller/releases/latest">Download APK (v1.0)</a> •
  <a href="#-architecture--engineering">Architecture</a> •
  <a href="#-benchmarks--ram-comparison">Benchmarks</a> •
  <a href="#-features">Features</a> •
  <a href="#-lineageos--redmi-2-setup">Setup Guide</a>
</p>

---

## 💡 Overview

Modern screen control and "wave to wake" applications on the Google Play Store frequently consume **40 MB to 100 MB of RAM**, bundle Firebase analytics, Google Mobile Services (GMS), and ad SDKs that trigger continuous Garbage Collection cycles. 

On legacy devices like the **Xiaomi Redmi 2 (1 GB - 2 GB RAM / Snapdragon 410)** running **LineageOS 14.1 (Android 7.1.2)**, heavy background apps degrade performance and lead to immediate termination by the Low Memory Killer (LMK).

**Proximity Controller** was engineered from scratch with a single architectural obsession: **Extreme RAM & Resource Optimization**. The entire APK is **~23.6 KB**, requires **zero external dependencies**, and operates under **3 MB RAM** in the background.

---

## 📊 Benchmarks & RAM Comparison

| Metric | Typical Play Store App | **Proximity Controller** | Advantage |
|:---|:---:|:---:|:---:|
| **APK Binary Size** | 6.5 MB – 22 MB | **23.6 KB** | **> 300x Smaller** |
| **Active Background RAM** | 45 MB – 95 MB | **< 3.0 MB** | **> 20x Lower RAM** |
| **Object Allocation in Sensor Loop** | 100+ objects/sec | **0 (`new` eliminated)** | **Zero GC Stutter** |
| **Google Play Services (GMS)** | Required | **None (100% Standalone)** | Fully De-Googled |
| **Advertising & Analytics SDKs** | 3 – 8 Trackers | **0 (Zero)** | 100% Private & Offline |
| **Android Framework Layer** | AndroidX + Jetpack | **Native AOSP APIs only** | Zero Class Overhead |

---

## 🎨 Minimalist UI Layout

Designed according to **Minimalism & Swiss UI** guidelines with a dark surface theme (`#0F172A`), high contrast typography, and clear visual hierarchy:

```text
┌──────────────────────────────────────────────┐
│  Proximity Controller                        │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │ Service enabled                        │  │
│  │ ● Running                      [ ON ]  │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  PROXIMITY SENSOR TASK                       │
│  ┌────────────────────────────────────────┐  │
│  │ (●) Screen On                          │  │
│  │     Wake screen when hand approaches   │  │
│  │ ────────────────────────────────────── │  │
│  │ ( ) Screen Off                         │  │
│  │     Lock screen when sensor is covered │  │
│  │ ────────────────────────────────────── │  │
│  │ ( ) Screen On & Off                    │  │
│  │     Combine both tasks                 │  │
│  └────────────────────────────────────────┘  │
│                                              │
│  ┌────────────────────────────────────────┐  │
│  │ ⓘ Screen Off requires Device Admin.   │  │
│  └────────────────────────────────────────┘  │
└──────────────────────────────────────────────┘
```

---

## ⚙️ Features

### 1. ☀️ Screen On (Wave to Wake)
- Automatically illuminates and awakens the display when your hand approaches or waves across the proximity sensor.
- Uses `PowerManager.WakeLock` with `SCREEN_BRIGHT_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP` timed strictly to 3 seconds.

### 2. 🌙 Screen Off (Pocket / Cover to Lock)
- Safely turns off and locks the display when the proximity sensor is fully covered (e.g., placing the phone face down or into a pocket).
- Uses Android's native `DevicePolicyManager.lockNow()` via standard Device Administrator privileges.

### 3. 🔄 Screen On & Off (Combined Mode)
- Seamless dual operation: locks when covered, wakes when the obstruction is removed.

---

## 🔬 Architecture & Engineering

### 1. Zero Garbage Collection (GC) in Hot Path
In standard Android implementations, `onSensorChanged(SensorEvent event)` creates boxed floats, string formatters, or intent instances. Because the proximity hardware can fire rapidly, this triggers memory churn and Garbage Collection latency.
- In **Proximity Controller**, all variables (`sensorDistance`, `WakeLock`, `DevicePolicyManager`, `ComponentName`) are pre-allocated once at the class level.
- `onSensorChanged()` processes primitive floats directly from the OS-provided buffer with **zero runtime memory allocation**.

### 2. Foreground Service with Low Priority
Runs as an ongoing Android `Service` bound to `startForeground()` with `Notification.PRIORITY_MIN`. This guarantees LineageOS's aggressive process reaper will not terminate the listener while maintaining a quiet, non-intrusive status bar footprint.

### 3. Pure Vector Asset Architecture
All icons and shapes are authored as pure XML vectors (`VectorDrawable`). No heavy PNG/WebP raster images are packed in the APK, eliminating image decoding caches from device memory.

```mermaid
flowchart TD
    Sensor["Proximity Sensor Hardware"] -->|Hardware Event| Listener["ProximityService (SensorEventListener)"]
    Listener -->|Near (< 1.0 cm)| ActionCheck{Task Mode}
    
    ActionCheck -->|Screen On| WL["Acquire Pre-allocated WakeLock (3s)"]
    ActionCheck -->|Screen Off| DPM["DevicePolicyManager.lockNow()"]
    ActionCheck -->|Both| Dual["Toggle Screen State"]
    
    WL --> Display["Device Screen Awakens"]
    DPM --> DisplayOff["Device Screen Locks"]
```

---

## 📲 LineageOS / Redmi 2 Setup Guide

To ensure persistent 24/7 background operation on custom ROMs:

1. **Install APK**: Download [`ProximityController-v1.0.apk`](https://github.com/ArtixForLiving/Proximity-Controller/releases/download/v1.0/ProximityController-v1.0.apk) and install via your file manager.
2. **Grant Device Admin** *(for Screen Off tasks)*:
   - When enabling the service in the app, click **Activate** when prompted by the system.
   - Alternatively, navigate to: `Settings` → `Security` → `Device Administrators` → Enable **Proximity Controller**.
3. **Disable Battery Optimization (Doze)**:
   - Navigate to: `Settings` → `Battery` → Top Right Menu (⋮) → `Battery optimization`.
   - Select **All apps** → Locate **Proximity Controller** → Choose **Don't optimize**.

---

## 🛠️ Building from Source

### Prerequisites
- **JDK**: OpenJDK 11 or 17 LTS
- **Android SDK**: Platform API 25, Build-Tools 30.0.3

### Build Steps
```bash
# Clone the repository
git clone https://github.com/ArtixForLiving/Proximity-Controller.git
cd Proximity-Controller

# Build Debug APK with R8 Shrinking & Minification
./gradlew assembleDebug
```

The compiled binary will be located at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## 📋 Permissions Summary

| Permission | Reason |
|:---|:---|
| `WAKE_LOCK` | Required to illuminate and wake up the display. |
| `FOREGROUND_SERVICE` | Required to maintain high process priority against Android LMK. |
| `BIND_DEVICE_ADMIN` | Required strictly to invoke `lockNow()` for the Screen Off feature. |

---

## 📄 License

This project is licensed under the [MIT License](LICENSE) — free for personal, educational, and commercial modification.
