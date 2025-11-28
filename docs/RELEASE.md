# Speed/Limit Release Guide

## Creating a Release Build for Google Play

### Step 1: Create a Signing Keystore (One-Time Setup)

Open a terminal and run:

```bash
keytool -genkey -v -keystore speedlimit-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias speedlimit
```

You'll be prompted for:
- Keystore password (remember this!)
- Your name, organization, etc.
- Key password (can be same as keystore password)

**IMPORTANT:** Store this keystore file securely. If you lose it, you cannot update your app on Google Play.

### Step 2: Configure Signing in Android Studio

1. Open Android Studio
2. Go to **Build > Generate Signed Bundle / APK**
3. Choose **Android App Bundle** (recommended for Play Store) or **APK**
4. Click **Next**
5. Select your keystore file, enter passwords
6. Choose **release** build variant
7. Click **Finish**

### Step 3: Alternative - Configure in build.gradle.kts

Add to `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("../speedlimit-release-key.jks")
            storePassword = System.getenv("KEYSTORE_PASSWORD") ?: ""
            keyAlias = "speedlimit"
            keyPassword = System.getenv("KEY_PASSWORD") ?: ""
        }
    }
    
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}
```

Then build from command line:
```bash
./gradlew bundleRelease
```

Output: `app/build/outputs/bundle/release/app-release.aab`

### Step 4: Upload to Google Play Console

1. Go to [Google Play Console](https://play.google.com/console)
2. Create app > Enter details from `STORE_LISTING.md`
3. Go to **Production > Create new release**
4. Upload the `.aab` file
5. Add release notes
6. Review and roll out

## Version Checklist Before Release

- [ ] Update `versionCode` and `versionName` in `app/build.gradle.kts`
- [ ] Test on real device
- [ ] Check all permissions work correctly
- [ ] Verify Firebase is receiving events
- [ ] Update privacy policy if needed
- [ ] Prepare screenshots for store listing

## Keystore Security

**DO NOT:**
- Commit keystore to Git
- Share keystore passwords in plain text
- Lose the keystore file

**DO:**
- Store keystore in a secure location (password manager, secure cloud storage)
- Keep a backup
- Use environment variables for CI/CD

Add to `.gitignore`:
```
*.jks
*.keystore
```

