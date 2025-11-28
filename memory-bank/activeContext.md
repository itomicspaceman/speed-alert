# Active Context: Speed/Limit

## Current State
The Speed/Limit app is **fully functional** with comprehensive features and legal protection. Version 2.4 is the current release.

## Recent Session Work (Nov 28, 2025)

### Completed This Session
1. **Package rename** - `com.speedalert` → `com.speedlimit`
2. **App name** - Changed to "Speed/Limit"
3. **Floating overlay mode** - Minimized speed display over other apps
4. **Legal disclaimer** - Shows every launch, 85 language translations
5. **Auto-show floating** - Overlay reappears when speeding detected
6. **Larger speed limit buttons** - 72sp text for better tap targets
7. **Cursor Memory Bank** - Full documentation system

### Key Files Modified
- `DisclaimerActivity.kt` - Shows every launch, no persistence
- `FloatingSpeedService.kt` - Foreground service with overlay
- `SpeedMonitorService.kt` - Auto-triggers floating display on alert
- `MainActivity.kt` - Floating mode button, permission handling
- `85 strings.xml files` - Translations via Google Translate API

## Current Focus
Project is feature-complete for POC. Ready for real-world testing.

## Next Steps (Suggested Priority)

### High Priority
1. **Real-world road testing** - Test on actual drives
2. **Custom app icon** - Design distinctive launcher icon
3. **Play Store preparation** - Screenshots, description, privacy policy

### Medium Priority
4. **Crowdsourcing backend** - API for speed limit contributions
5. **Offline caching** - Store speed limits for areas without connectivity
6. **User preferences** - Settings screen for customization

### Low Priority
7. **Widget** - Home screen speed display
8. **Wear OS** - Smartwatch companion
9. **Android Auto** - Vehicle integration

## Active Decisions

### Resolved
- **Package name**: `com.speedlimit`
- **App display name**: "Speed/Limit"
- **Disclaimer**: Shows every launch (max legal protection)
- **Languages**: 85 translations via Google Translate API
- **Floating overlay**: Auto-shows when speeding after dismissal
- **Speed limit grid**: 72sp buttons with FlexboxLayout

### Technical Notes
- Floating service requires `FOREGROUND_SERVICE_SPECIAL_USE` on Android 14+
- Overlay layouts can't use theme attributes like `?attr/...`
- Google Translate API script saved at `scripts/translate-strings.js`

## Testing Notes
- Use Android Emulator's Extended Controls > Location > Routes
- Change emulator language in Settings to test translations
- Test floating overlay permission flow on first use
