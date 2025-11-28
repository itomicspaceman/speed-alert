# Speed/Limit - Android Speed Limit Notification App

A proof-of-concept Android app that monitors your driving speed and alerts you when exceeding speed limits.

## Features

- **Background Speed Monitoring**: Runs as a foreground service, continues working when minimized
- **GPS Speed Detection**: Uses high-accuracy GPS to track your speed
- **OpenStreetMap Speed Limits**: Queries Overpass API for road speed limit data
- **Smart Alerts**: Only checks speed limits when traveling above 20 mph
- **5% Tolerance**: Alerts when speed exceeds limit by more than 5%
- **Loud Audio + Vibration**: Unmissable alerts using system alarm tones

## Requirements

- Android 8.0 (API 26) or higher
- GPS/Location enabled on device
- Internet connection (for speed limit lookups)

## Building the App

### Prerequisites

1. **Install Android Studio** from [developer.android.com](https://developer.android.com/studio)
   - This installs the Android SDK automatically
   - Default installation is fine

2. **First-Time Setup (Required)**
   - Open Android Studio
   - File > Open > Select this `speed` folder
   - Android Studio will download Gradle and dependencies automatically
   - Wait for "Gradle sync" to complete (may take a few minutes)

3. **Verify SDK Location**
   - After Android Studio installs, check `local.properties` file
   - Update `sdk.dir` path if your SDK is in a different location

### Build Steps

#### Option A: Command Line (Recommended)

Open PowerShell in this folder and run:

```powershell
.\gradlew.bat assembleDebug
```

The APK will be at: `app\build\outputs\apk\debug\speed-limit-1.0.apk`

#### Option B: Android Studio

1. Open Android Studio
2. File > Open > Select this folder
3. Wait for Gradle sync to complete
4. Build > Build Bundle(s) / APK(s) > Build APK(s)
5. Click "locate" in the notification to find the APK

### Installing on Your Phone

1. **Enable Developer Options** on your Android phone:
   - Go to Settings > About Phone
   - Tap "Build Number" 7 times
   - Developer Options will appear in Settings

2. **Enable Unknown Sources**:
   - Settings > Security (or Privacy)
   - Enable "Install from Unknown Sources" or "Install unknown apps"

3. **Transfer the APK**:
   - Email the APK to yourself and open on phone
   - Upload to Google Drive/OneDrive and download on phone
   - Connect phone via USB and copy file

4. **Install**:
   - Tap the APK file on your phone
   - Confirm installation when prompted

## Usage

1. Open the app
2. Grant location permissions when prompted
   - Select "Allow all the time" for background monitoring
3. Tap **Start Monitoring**
4. The app will:
   - Show your current speed
   - Display detected speed limit when above 20 mph
   - Alert with sound + vibration if you exceed the limit by 5%

## Permissions Explained

| Permission          | Why Needed                                  |
|:--------------------|:--------------------------------------------|
| Location            | To track your speed via GPS                 |
| Background Location | To work when app is minimized               |
| Internet            | To fetch speed limits from OpenStreetMap    |
| Notifications       | To show the persistent service notification |
| Vibrate             | For alert vibrations                        |

## Limitations (POC)

- **OSM Coverage**: Not all roads have speed limit data in OpenStreetMap
- **Data Accuracy**: Community-contributed data may occasionally be incorrect
- **Network Required**: Speed limits are fetched live (with caching)
- **UK-Focused**: Speed limit parsing assumes UK conventions (mph)

## Technical Notes

- Uses Overpass API (free, no API key required)
- 30-second cache to reduce API calls
- Queries roads within 50m of current position
- Handles various OSM speed formats (mph, km/h, "national", etc.)

## Troubleshooting

**App doesn't start monitoring:**
- Check location permission is granted
- Ensure GPS is enabled on device

**No speed limit shown:**
- You may be in an area without OSM speed data
- Check internet connection

**Alerts not working:**
- Check phone isn't on silent/vibrate only
- Ensure alarm volume is turned up

