# Technical Context: Speed/Limit

## Technology Stack

### Core
- **Language**: Kotlin 1.9.20
- **Platform**: Android (minSdk 26, targetSdk 35)
- **Build System**: Gradle 8.2 with Kotlin DSL
- **IDE**: Android Studio (Cursor for code editing)

### Dependencies
```kotlin
// AndroidX Core
androidx.core:core-ktx:1.15.0
androidx.appcompat:appcompat:1.7.0

// UI Components
com.google.android.material:material:1.12.0
androidx.constraintlayout:constraintlayout:2.1.4
com.google.android.flexbox:flexbox:3.0.0

// Splash Screen
androidx.core:core-splashscreen:1.0.1

// Async
org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3

// Networking
com.squareup.okhttp3:okhttp:4.12.0
org.json:json:20231013

// Location
com.google.android.gms:play-services-location:21.0.1
```

## Development Environment

### Requirements
- Android Studio (for SDK and APK building)
- JDK 17 (bundled with Android Studio)
- Android SDK 35
- Windows 11 with Laravel Herd (project location)
- Node.js (for translation script)

### Build Commands
```powershell
# Build debug APK
.\gradlew.bat assembleDebug

# Output location
app\build\outputs\apk\debug\SpeedLimit-{version}.apk
```

### Project Structure
```
speed/
├── app/
│   ├── build.gradle.kts          # Module config
│   └── src/main/
│       ├── AndroidManifest.xml   # Permissions, services
│       ├── java/com/speedlimit/
│       │   ├── MainActivity.kt
│       │   ├── DisclaimerActivity.kt
│       │   ├── SpeedMonitorService.kt
│       │   ├── FloatingSpeedService.kt
│       │   ├── SpeedLimitProvider.kt
│       │   ├── SpeedUnitHelper.kt
│       │   └── AlertPlayer.kt
│       └── res/
│           ├── layout/           # XML layouts
│           ├── drawable/         # Icons, vectors
│           ├── values/           # Default strings, colors, themes
│           ├── values-XX/        # 85 language translations
│           └── mipmap-anydpi-v26/ # Adaptive icons
├── scripts/
│   └── translate-strings.js      # Google Translate API script
├── memory-bank/                  # Project documentation
├── build.gradle.kts              # Project config
├── settings.gradle.kts
└── local.properties              # SDK path
```

## Android Permissions
```xml
ACCESS_FINE_LOCATION      <!-- GPS -->
ACCESS_COARSE_LOCATION    <!-- Backup location -->
ACCESS_BACKGROUND_LOCATION <!-- Background GPS -->
FOREGROUND_SERVICE        <!-- Background service -->
FOREGROUND_SERVICE_LOCATION <!-- Location in service -->
FOREGROUND_SERVICE_SPECIAL_USE <!-- Floating overlay service -->
SYSTEM_ALERT_WINDOW       <!-- Floating window overlay -->
INTERNET                  <!-- OSM API -->
POST_NOTIFICATIONS        <!-- Service notification -->
WAKE_LOCK                 <!-- Keep CPU active -->
VIBRATE                   <!-- Alert vibration -->
```

## External APIs

### OpenStreetMap Overpass API
- **Endpoint**: https://overpass-api.de/api/interpreter
- **Auth**: None required (public API)
- **Rate Limit**: Courtesy limit, no hard cap
- **Data**: Community-contributed speed limits

### Android Geocoder
- Built-in reverse geocoding
- Used for country detection
- Requires internet connection

### Google Translate API
- Used one-time for generating translations
- Script at `scripts/translate-strings.js`
- Requires API key (not stored in repo)

## Technical Constraints

### Battery Considerations
- GPS is battery-intensive
- Caching reduces API calls
- Foreground services required for reliability

### OSM Data Quality
- Coverage varies by region
- Some roads lack speed limit data
- Urban areas better than rural

### Android Restrictions
- Background location needs "Allow all the time"
- Android 13+ requires notification permission
- Android 14+ requires foreground service types
- Overlay permission requires manual grant in Settings
- Service layouts can't use theme attributes (?attr/...)

## Localization
- 85 language translations
- Android auto-selects based on device locale
- Falls back to English if no translation exists
- Translations generated via Google Translate API
