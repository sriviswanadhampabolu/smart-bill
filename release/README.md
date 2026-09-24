# SmartBill Release Downloads & Installation Guide

This folder contains pre-built Android application packages (`.apk`) for **SmartBill**.

---

## 📱 Latest Release: v1.1.0

| File | Size | Architecture | Target SDK | SHA-256 Checksum |
| :--- | :--- | :--- | :--- | :--- |
| **[`SmartBill-v1.1.0.apk`](./SmartBill-v1.1.0.apk)** | ~36.9 MB | universal (arm64-v8a, armeabi-v7a, x86_64) | Android 15 (API 35) / Min Android 7.0 (API 24) | `165328922E5882FBF8D94A4E47FEBFB21A3E2DD17B2F55916F95B785B8C10D59` |

---

## 🌟 What's New in v1.1.0

- 🎨 **New Official Brand Logo & App Icon**:
  - Replaced launcher icons across all screen densities (MDPI, HDPI, XHDPI, XXHDPI, XXXHDPI) with the new official SmartBill logo featuring the illuminated lightning-receipt emblem.
  - Integrated the official brand logo into the in-app headers and login screen.
- 🧠 **TensorFlow Lite MobileNetV3 Deep Learning Embedder**:
  - Replaced the legacy hand-crafted color histograms with an actual pre-trained **MobileNetV3 TFLite deep neural network**.
  - Extracts **1,000-dimensional semantic latent vectors** invariant to varied counter backgrounds (table, hand, shelf), dynamic lighting (glare, shadows, dim lighting), and camera angles.
- ⚡ **Zero-Latency In-Memory Vector Search**:
  - Calibrated cosine similarity thresholds (`HIGH = 0.52`, `LOW = 0.28`) for instant item identification.
- 🔄 **Auto-Upgrade Migration**:
  - Automatically re-embeds previously photographed items using saved thumbnails upon app startup.

---

## 📱 Previous Releases

- **[`SmartBill-v1.0.0.apk`](./SmartBill-v1.0.0.apk)** (~35.5 MB)

---

## 🚀 How to Install on Your Android Phone

1. **Download the APK**:
   - Download [`SmartBill-v1.1.0.apk`](./SmartBill-v1.1.0.apk) directly to your device or transfer it via USB / Quick Share / WhatsApp.
2. **Enable Unknown Sources** (First time only):
   - Open **Settings** &rarr; **Security** (or **Apps & notifications**).
   - Tap **Install unknown apps** and toggle **Allow from this source** for your browser or file manager.
3. **Install**:
   - Tap on the downloaded `SmartBill-v1.1.0.apk` file and select **Install**.
4. **Permissions**:
   - Grant **Camera permission** when prompted to enable real-time visual item detection and barcode scanning.
