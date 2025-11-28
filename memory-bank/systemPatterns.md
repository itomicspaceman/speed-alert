# System Patterns: Speed Alert

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    MainActivity                          │
│  - UI display (speed, limit, grid)                      │
│  - Permission handling                                   │
│  - Start/stop service control                           │
└─────────────────────┬───────────────────────────────────┘
                      │ BroadcastReceiver
                      ▼
┌─────────────────────────────────────────────────────────┐
│              SpeedMonitorService                         │
│  - Foreground service (survives app minimize)           │
│  - GPS location listener                                │
│  - Coordinates speed checking                           │
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
```

## Key Design Patterns

### Foreground Service Pattern
- `SpeedMonitorService` runs as Android foreground service
- Required for continuous GPS access when app minimized
- Shows persistent notification while active
- Uses `START_STICKY` to restart if killed

### Broadcast Communication
- Service broadcasts speed updates to Activity
- Decouples UI from background logic
- Activity registers/unregisters receiver on resume/pause
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

## Component Responsibilities

### MainActivity.kt
- Lifecycle management
- Permission request flows (location, background, notification)
- Dynamic UI generation (FlexboxLayout for speed grid)
- Receives broadcasts, updates display
- Handles start/stop button

### SpeedMonitorService.kt
- Foreground service management
- GPS location updates (1 second interval)
- Coordinates speed limit lookups
- Triggers alerts when over limit
- Broadcasts updates to UI

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

