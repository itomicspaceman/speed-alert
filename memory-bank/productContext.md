# Product Context: Speed/Limit

## Problem Statement
Drivers often unintentionally exceed speed limits due to:
- Not noticing speed limit signs
- Unfamiliar roads
- Distracted driving
- Gradual speed creep on long journeys

This leads to speeding tickets, accidents, and unsafe driving conditions.

## Solution
A passive speed monitoring app that:
1. Runs silently in background while driving
2. Only activates when speed exceeds 20 mph (to ignore walking/parking)
3. Looks up the current road's speed limit from OpenStreetMap
4. Alerts loudly when exceeding the limit by 5%+
5. Displays current speed and limit for driver awareness
6. Offers floating overlay for minimal distraction

## How It Works

### User Journey
1. Open app → Accept disclaimer (every launch)
2. Tap play to start monitoring (or tap floating icon for minimal mode)
3. Put phone in car mount or pocket
4. Drive normally
5. If speeding detected → loud alarm + vibration + flashing display
6. Floating overlay auto-appears if previously dismissed
7. Slow down → alert stops
8. Tap stop or close app when done driving

### Data Flow
```
GPS Location → Speed Calculation → 
  If speed > 20mph → Query OSM for road's maxspeed →
    If speed > limit + 5% → Trigger Alert + Show Floating
```

## User Experience Goals

### Visual Design
- Pure black background (reduces glare, saves battery on OLED)
- Large white numbers (readable at a glance)
- Flashing red/white when over limit
- Minimal UI elements
- Floating overlay for use with other apps

### Audio Design
- Uses system alarm stream (cuts through music/calls)
- Repeating urgent tone pattern
- 5-second cooldown between alerts (not continuous annoying)

### International Support
- Auto-detects country from GPS
- Displays speed in local units (mph or km/h)
- Shows country-appropriate speed limit options
- 85 languages for legal disclaimer

### Legal Protection
- Disclaimer shown EVERY app launch
- 85 language translations
- Clear liability waiver
- Safety warnings about not using while driving
- Users must accept to proceed

## Target Users
- Everyday drivers wanting speed awareness
- People driving in unfamiliar areas
- Those who've received speeding tickets
- Safety-conscious drivers
- International travelers

## Competitive Landscape
- Waze: Has speed limit display but no alerts in free version
- Google Maps: Shows limits but no alerting
- Dedicated GPS units: Expensive, need separate device
- Speed/Limit: Free, simple, focused on one job, floating overlay

## Key Differentiators
1. **Floating overlay** - Use while navigating with other apps
2. **Auto-show on alert** - Overlay reappears when you need it
3. **85 languages** - Maximum legal protection globally
4. **Every-launch disclaimer** - Clear legal protection
5. **Country-aware** - Automatic unit and limit detection
