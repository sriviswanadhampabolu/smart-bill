# SmartBill Release Downloads & Installation Guide

This folder contains pre-built Android application packages (`.apk`) for **SmartBill**.

---

## 📱 Latest Release: v1.3.0

| File | Size | Architecture | Target SDK | SHA-256 Checksum |
| :--- | :--- | :--- | :--- | :--- |
| **[`SmartBill-v1.3.0.apk`](./SmartBill-v1.3.0.apk)** | ~37.4 MB | universal (arm64-v8a, armeabi-v7a, x86_64) | Android 15 (API 35) / Min Android 7.0 (API 24) | `90A8F99C3CE8A6E17151DEFF0242A25C527D9DCADEEE3FA662B37D0CB74062A2` |

---

## 🌟 What's New in v1.3.0

- 🔐 **One-Time User Login & Register (Persistent Session)**:
  - User details & security tokens saved locally on the device; users do not need to login repeatedly.
  - Reopening the app smoothly verifies lock credentials without re-entering mobile credentials.
  - Full **Logout** action available anytime, which securely resets session and displays Login & Register screens again.
- 🌐 **Online Neon Cloud Database Storage**:
  - Live PostgreSQL database storage on Neon cloud (`neondb`).
  - Stores user account, shopkeeper profile, address, UPI IDs, catalog items, and billing records in cloud with automatic synchronization.
- ⚡ **Google Authorization Sign-In & Register**:
  - Instant one-tap "Sign in with Google" / "Register with Google" button.
  - Seamlessly links Google account to Neon online database profile.
- 🏪 **Store Registration & Shopkeeper Profile**:
  - After registering or first login, prompts for complete store details:
    - **Shopkeeper Name**
    - **Store Name**
    - **Phone Number**
    - **Store Address**
    - **Primary UPI ID**
    - **4-Digit Counter Unlock PIN**
- 👤 **Dedicated User Account Dashboard**:
  - View and edit Shopkeeper Name, Store Name, Phone Number, Store Address, Primary UPI ID, and Neon Cloud connection status.
- 📲 **UPI Payment QR Code Management (Up to 6 QR Files)**:
  - Upload up to 6 custom QR code image files (GPay, PhonePe, Paytm, BHIM, Counter QR).
  - Preview QR codes, set the active default QR, and delete QR code files with safety confirmation.
- 👆 **Fingerprint / Biometric App Unlock**:
  - Integrated Android `BiometricPrompt` for instant one-touch counter unlocking using device fingerprint sensor.
  - Configurable toggle in the User Account Dashboard, with 4-digit PIN fallback.
- 🏷️ **Main Screen Dashboard Button Polish**:
  - Resolved clipping issues on the 3 primary action cards on the main screen.
  - Distinct, bold operation names and descriptive subtitles are prominently visible: **Inventory** (*Stock & Alerts*), **Reports** (*Analytics*), and **Past Bills** (*History*).
- 📈 **Dual-Mode Business Reports (Price-based & Quantity-based)**:
  - 3-way segmented analytics toggle:
    1. **By Revenue (₹)**: Highest value / price-selling products ranked by gross earnings.
    2. **By Quantity (Units/Kg)**: Volume-based leaderboard showing top selling items by count/weight sold, regardless of unit price.
    3. **Top Categories (Qty)**: Product categories ranked by sales volume.

---

## 📱 Previous Releases

- **[`SmartBill-v1.2.0.apk`](./SmartBill-v1.2.0.apk)** (~37.1 MB)
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
