# Speed/Limit - Android Speed Limit Notification App

A feature-rich Android app that monitors your driving speed and alerts you when exceeding speed limits. Uses crowdsourced data from OpenStreetMap.

![App Icon](app/src/main/ic_launcher-playstore.png)

## Features

### Core Features (Free)
- **Real-time Speed Monitoring**: GPS-based speed detection with foreground service
- **Smart Speed Limit Detection**: Queries OpenStreetMap Overpass API with intelligent caching
- **Visual & Audio Alerts**: Flashing red/white display + loud audio when over limit
- **Floating Overlay Mode**: Compact display that floats over other apps
- **International Support**: Auto-detects country and displays mph/km/h accordingly
- **85+ Language Support**: Legal disclaimer translated for global coverage
- **OSM Crowdsourcing**: Contribute speed limits using your OpenStreetMap account

### Premium Features (Coming Soon)
- **Voice Announcements**: Android TTS announces limit changes, unknown zones, and over-limit warnings
- **Ad-Free Experience**: No interstitial ads
- **Contribution Stats**: Track your OSM contributions

## Screenshots

*Coming soon*

## Requirements

- Android 8.0 (API 26) or higher
- GPS/Location enabled
- Internet connection (for speed limit lookups)

## Building the App

### Prerequisites

1. **Install Android Studio** from [developer.android.com](https://developer.android.com/studio)
2. **Open the Project**: File > Open > Select this folder
3. **Wait for Gradle Sync** to complete

### Build Debug APK

#### Option A: Android Studio

1. Build > Generate Signed Bundle / APK > APK
2. Choose "debug" build variant
3. APK location: `app/build/outputs/apk/debug/SpeedLimit-X.X.apk`

#### Option B: Command Line

```powershell
.\gradlew.bat assembleDebug
```

### Install on Phone

1. Enable **Developer Options** (Settings > About Phone > Tap "Build Number" 7 times)
2. Enable **Install from Unknown Sources** (Settings > Security)
3. For **Pixel/Android 13+**: After first install attempt, go to Settings > Apps > Speed/Limit > ⋮ > "Allow restricted settings" to enable overlay permission
4. Transfer APK via Google Drive, email, or USB
5. Tap to install

## Development

### Project Structure

```
app/src/main/
├── java/com/speedlimit/
│   ├── MainActivity.kt          # Main UI
│   ├── DisclaimerActivity.kt    # Legal disclaimer (shown every launch)
│   ├── SettingsActivity.kt      # Settings screen
│   ├── SpeedMonitorService.kt   # Foreground service for GPS
│   ├── FloatingSpeedService.kt  # Floating overlay service
│   ├── SpeedLimitProvider.kt    # Overpass API + smart caching
│   ├── SpeedUnitHelper.kt       # mph/km/h detection & conversion
│   ├── VoiceAnnouncer.kt        # TTS voice announcements
│   ├── OsmContributor.kt        # OSM OAuth & contributions
│   ├── AlertPlayer.kt           # Audio alerts
│   └── AnalyticsHelper.kt       # Firebase integration
├── res/
│   ├── layout/                  # UI layouts
│   ├── values/strings.xml       # English strings
│   └── values-XX/strings.xml    # 85+ language translations
└── docs/
    ├── privacy-policy.html      # Privacy policy (GitHub Pages)
    ├── STORE_LISTING.md         # Play Store assets
    └── RELEASE.md               # Release build guide
```

### Key Technologies

- **Kotlin 2.0.21** with Coroutines
- **Material 3** design
- **Firebase Analytics & Crashlytics** for monitoring
- **OpenStreetMap Overpass API** for speed limits
- **Android TTS** for voice announcements
- **FlexboxLayout** for dynamic speed limit grid

### Smart Caching

The app uses intelligent caching to reduce API calls by ~70-80%:
- Caches by OSM way ID (road segment)
- 500m safety net for limit changes
- 2-minute maximum cache age
- Exponential backoff on rate limits

## Privacy

See [Privacy Policy](docs/privacy-policy.html)

**Key Points:**
- Location data processed on-device only
- No personal data collected or stored
- Anonymous usage analytics via Firebase
- OSM contributions use your own OSM account

## Contributing

Contributions welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Submit a pull request

## License

*To be determined*

## Acknowledgments

- **OpenStreetMap** for speed limit data
- **Overpass API** for free API access
- All OSM contributors worldwide

---

**Disclaimer**: Speed/Limit uses crowdsourced data that may be incomplete or inaccurate. Always refer to official road signs. The developers accept no liability for any consequences arising from use of this app.
