# Active Context: Speed Alert

## Current State
The Speed Alert POC is **fully functional** with all core features implemented. The app successfully monitors speed, detects limits from OSM, and alerts when speeding.

## Recent Session Work

### Completed This Session
1. **Country-aware speed units** - Auto-detects country, displays mph or km/h
2. **Dynamic speed limit grid** - FlexboxLayout with country-specific limits
3. **40+ country database** - Common speed limits for each country
4. **Cursor Memory Bank** - Project documentation system initialized

### Key Files Modified
- `SpeedUnitHelper.kt` - Added country database, merged limit logic
- `MainActivity.kt` - Dynamic grid generation with FlexboxLayout
- `activity_main.xml` - Replaced GridLayout with FlexboxLayout
- `build.gradle.kts` - Added flexbox dependency

## Current Focus
Setting up Memory Bank documentation for project continuity.

## Next Steps (Suggested Priority)

### High Priority
1. **Test on physical device** - Verify real GPS behavior
2. **Crowdsourcing feature** - Backend for user speed limit contributions
3. **OSM enrichment query** - Discover local limits dynamically

### Medium Priority
4. **Offline caching** - Store speed limits for areas without connectivity
5. **User preferences** - Manual unit override, alert threshold adjustment
6. **Trip history** - Log speeding incidents

### Low Priority
7. **Widget** - Home screen speed display
8. **Wear OS** - Smartwatch companion
9. **CarPlay/Android Auto** - Vehicle integration

## Active Decisions

### Resolved
- **Unit system**: Internal mph, display converts based on country
- **Grid approach**: FlexboxLayout for dynamic country-specific limits
- **Alert threshold**: 5% over limit before alerting
- **Speed check threshold**: Only check limits above 20 mph

### Open Questions
- How to handle crowdsourced data backend? (Firebase vs Laravel vs OSM direct)
- Should we cache OSM data for offline use?
- Need user preference for alert sensitivity?

## Known Issues
- None blocking - app is functional
- OSM coverage varies - some roads have no limit data
- Emulator GPS simulation can be slow

## Testing Notes
- Use Android Emulator's Extended Controls > Location > Routes
- Set location to different countries to test unit switching
- Sydney: -33.87, 151.21 (Australia, km/h)
- Berlin: 52.52, 13.40 (Germany, km/h)  
- London: 51.51, -0.13 (UK, mph)

