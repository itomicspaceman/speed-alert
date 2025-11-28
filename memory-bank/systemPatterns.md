# System Patterns: Speed/Limit

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                 DisclaimerActivity                       │
│  - Shows every launch (legal protection)                │
│  - 85 language translations                             │
│  - Accept → MainActivity, Decline → Exit                │
└─────────────────────┬───────────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────────┐
│                    MainActivity                          │
│  - UI display (speed, limit, grid)                      │
│  - Permission handling                                   │
│  - Start/stop service control                           │
│  - Floating mode toggle                                 │
└─────────────────────┬───────────────────────────────────┘
                      │ BroadcastReceiver
                      ▼
┌─────────────────────────────────────────────────────────┐
│              SpeedMonitorService                         │
│  - Foreground service (survives app minimize)           │
│  - GPS location listener                                │
│  - Coordinates speed checking                           │
│  - Auto-triggers FloatingSpeedService on alert          │
└──────┬──────────────────┬───────────────────┬───────────┘
       │                  │                   │
       ▼                  ▼                   ▼
┌──────────────┐  ┌───────────────┐  ┌───────────────┐
│SpeedLimit    │  │SpeedUnitHelper│  │AlertPlayer    │
│Provider      │  │               │  │               │
│- OSM queries │  │- Country      │  │- Audio tones  │
│- Caching     │  │  detection    │  │- Vibration    │
│              │  │- Unit convert │  │               │
└──────────────┘  └───────────────┘  └───────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│              FloatingSpeedService                        │
│  - Foreground service (SPECIAL_USE type)                │
│  - WindowManager overlay                                │
│  - Draggable floating widget                            │
│  - Receives speed broadcasts                            │
│  - Flashes red/white when over limit                    │
└─────────────────────────────────────────────────────────┘
```

## Key Design Patterns

### Foreground Service Pattern
- `SpeedMonitorService` and `FloatingSpeedService` run as foreground services
- Required for continuous GPS access and overlay display
- Shows persistent notification while active
- Uses `START_STICKY` to restart if killed

### Broadcast Communication
- Services broadcast speed updates
- Activities/Services register receivers
- Decouples UI from background logic
- Intent extras: speed, limit, isOverLimit, countryCode

### Caching Strategy
- **Speed limit cache**: 30 seconds, 100m distance threshold
- **Country cache**: 5 minutes, 10km distance threshold
- Reduces API calls and battery usage
- Falls back to cached values on API failure

### Country-Aware Processing
- `SpeedUnitHelper` detects country via reverse geocoding
- All internal calculations in mph (consistent)
- Converts to display units only at UI layer
- OSM parsing adapts to country's implicit units

### Floating Overlay Pattern
- Uses `SYSTEM_ALERT_WINDOW` permission
- `WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY`
- Touch listener distinguishes taps from drags
- Auto-show on alert via static flag

## Component Responsibilities

### DisclaimerActivity.kt
- Entry point (launcher activity)
- Shows legal disclaimer every launch
- Accept proceeds to MainActivity
- Decline closes app

### MainActivity.kt
- Main UI with speed display
- Permission request flows
- Dynamic speed limit grid (FlexboxLayout)
- Floating mode toggle button
- Receives broadcasts, updates display

### SpeedMonitorService.kt
- Foreground service management
- GPS location updates (1 second interval)
- Coordinates speed limit lookups
- Triggers alerts when over limit
- Auto-starts FloatingSpeedService on alert
- Broadcasts updates to UI

### FloatingSpeedService.kt
- Foreground service (SPECIAL_USE type)
- Creates WindowManager overlay
- Draggable floating widget
- Close button and double-tap handling
- Receives speed broadcasts
- Flashes when over limit

### SpeedLimitProvider.kt
- Overpass API queries
- Response parsing with country context
- Speed limit caching
- Handles API failures gracefully

### SpeedUnitHelper.kt
- Country detection (Geocoder + cache)
- Unit conversion functions
- 40+ country speed limit database
- OSM maxspeed parsing with unit awareness

### AlertPlayer.kt
- System alarm tone generation
- Vibration patterns
- Volume management
- Resource cleanup

## Data Models

### Speed Update Broadcast
```kotlin
Intent(ACTION_SPEED_UPDATE).apply {
    putExtra(EXTRA_SPEED, speedMph: Float)
    putExtra(EXTRA_SPEED_LIMIT, limitMph: Int)
    putExtra(EXTRA_IS_OVER_LIMIT, Boolean)
    putExtra(EXTRA_COUNTRY_CODE, String)
}
```

### OSM Overpass Query
```
[out:json][timeout:10];
way(around:50,{lat},{lon})["maxspeed"];
out tags;
```

## Error Handling
- API timeout: Return cached value or null
- Geocoding failure: Fall back to device locale
- No speed limit found: No alert (fail safe)
- Permission denied: Show rationale dialogs
- Overlay layout: No theme attributes (crashes in Service)
