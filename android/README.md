# SmartBill Android Client

High-speed, offline-first native Android counter billing app designed for retail, kirana, grocery, and general stores. Built with modern Android architecture, Jetpack Compose, CameraX visual item detection, Room DB, and bidirectional cloud sync.

---

## 🛠️ Tech Stack & Architecture

- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) with Material 3 design system
- **Language**: Kotlin 2.0+ (Coroutine-based async architecture)
- **Computer Vision**: CameraX + Custom illumination-invariant vector embedding engine (`ImageEmbedder`)
- **Local Persistence**: Room SQLite Database (Offline-first, 100% functional without internet)
- **Networking**: Retrofit 2 + OkHttp 4 + KotlinX Serialization
- **Barcode & QR**: Google ML Kit Barcode Scanning + ZXing for dynamic UPI payment QR generation
- **Localization**: Dynamic app-wide localization across 8 Indian languages (EN, HI, TA, TE, KN, MR, GU, BN)

---

## 📂 Architecture

```
android/
├── app/
│   ├── src/main/
│   │   ├── assets/             # Starter catalog JSON (groceries, biscuits, chocolates, stationery)
│   │   ├── java/com/grocer/billing/
│   │   │   ├── core/
│   │   │   │   ├── data/       # Room database, DAOs, entities, repositories
│   │   │   │   ├── lang/       # AppLanguageManager (dynamic multi-language switcher)
│   │   │   │   ├── network/    # Retrofit API clients & offline sync worker
│   │   │   │   └── vision/     # ImageEmbedder (lighting gain normalization, Sobel edge filter)
│   │   │   ├── feature/
│   │   │   │   ├── auth/       # Shopkeeper login, signup, PIN counter unlock
│   │   │   │   ├── billing/    # Instant counter billing, receipt builder, past bills
│   │   │   │   ├── inventory/  # Item management, angle training, popular item import
│   │   │   │   ├── khata/      # Customer credit ledger & settlement
│   │   │   │   ├── reports/    # Sales analytics, payment breakdown, top sellers
│   │   │   │   └── settings/   # Language selector, UPI setup, printer config
│   │   │   └── ui/theme/       # Color palette, typography, shapes
│   │   └── res/                # Launcher icons (SmartBill logo), strings, layouts
│   └── build.gradle.kts
├── gradle/
└── settings.gradle.kts
```

---

## 🚀 Building From Source

### Prerequisites

- **Android Studio**: Iguana (2023.2.1) or newer
- **JDK**: JDK 17 or JDK 21 (Bundled Android Studio JBR works great)
- **Android SDK**: API 34 (Android 14)

### Build Debug APK via Command Line

```bash
# Set Java and Android SDK paths if not set globally
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:ANDROID_HOME = "C:\Users\srivi\AppData\Local\Android\Sdk"

# Build debug APK
.\gradlew.bat assembleDebug
```

The APK will be generated at:
`app/build/outputs/apk/debug/app-debug.apk`

### Run Unit Tests

```bash
.\gradlew.bat testDebugUnitTest
```
