# SmartBill Release Downloads & Installation Guide

This folder contains pre-built Android application packages (`.apk`) for **SmartBill**.

---

## 📱 Latest Release: v1.2.0

| File | Size | Architecture | Target SDK | SHA-256 Checksum |
| :--- | :--- | :--- | :--- | :--- |
| **[`SmartBill-v1.2.0.apk`](./SmartBill-v1.2.0.apk)** | ~37.1 MB | universal (arm64-v8a, armeabi-v7a, x86_64) | Android 15 (API 35) / Min Android 7.0 (API 24) | `4ADDCF2C00CCB5E6C5F5F600F94B94749E2A289012AA075F3CCFD890C0D9FB89` |

---

## 🌟 What's New in v1.2.0

- 🗑️ **Inventory Item Deletion**:
  - Permanently remove items from inventory management with safety confirmation dialog.
  - Automatically cleans database, synchronizes deletion queue, and purges recognition embeddings from in-memory cache.
- 📊 **Monthly Sales Analytics**:
  - New executive Monthly Sales card in Reports Screen showing total month revenue, bill count, average bill value, and today's comparison.
  - Quick month-to-date sales badge on the Dashboard hero card.
- ⚠️ **Low-Stock Only Inventory View**:
  - One-tap filter chip in Inventory Management to isolate and view only items that need restocking.
- 🔔 **Custom Low-Stock Alert Limits**:
  - Set specific low-stock alert thresholds for individual items with quick presets (+2, +5, +10, etc.) or custom values.
- 📲 **Push Notifications for Depleted Stock**:
  - Automatic high-priority heads-up system notification delivered whenever an item reaches or drops below its alert limit.
- 🎨 **Redesigned Quick Action Badges**:
  - Replaced monochrome icons with vibrant, color-coded badges for **Inventory** (Kirana Emerald), **Reports** (Royal Indigo), and **Past Bills** (Warm Amber).
- 💡 **Camera Flash / Torch Control**:
  - Added a flash toggle button in the header and on-screen camera viewfinder for item photo capture and training.

---

## 📱 Previous Releases

- **[`SmartBill-v1.1.0.apk`](./SmartBill-v1.1.0.apk)** (~36.9 MB)
- **[`SmartBill-v1.0.0.apk`](./SmartBill-v1.0.0.apk)** (~35.5 MB)

---

## 🚀 How to Install on Your Android Phone

1. **Download the APK**:
   - Download [`SmartBill-v1.2.0.apk`](./SmartBill-v1.2.0.apk) directly to your device or transfer it via USB / Quick Share / WhatsApp.
2. **Enable Unknown Sources** (First time only):
   - Open **Settings** &rarr; **Security** (or **Apps & notifications**).
   - Tap **Install unknown apps** and toggle **Allow from this source** for your browser or file manager.
3. **Install**:
   - Tap on the downloaded `SmartBill-v1.2.0.apk` file and select **Install**.
4. **Permissions**:
   - Grant **Camera permission** for visual item detection and barcode scanning.
   - Grant **Notification permission** on Android 13+ to receive instant low-stock alerts.
