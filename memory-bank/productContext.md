# Product Context: Speed Alert

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

## How It Works

### User Journey
1. Open app, tap to start monitoring
2. Put phone in car mount or pocket
3. Drive normally
4. If speeding detected → loud alarm + vibration + flashing display
5. Slow down → alert stops
6. Tap to stop monitoring when done driving

### Data Flow
```
GPS Location → Speed Calculation → 
  If speed > 20mph → Query OSM for road's maxspeed →
    If speed > limit + 5% → Trigger Alert
```

## User Experience Goals

### Visual Design
- Pure black background (reduces glare, saves battery on OLED)
- Large white numbers (readable at a glance)
- Flashing red/white when over limit
- Minimal UI elements

### Audio Design
- Uses system alarm stream (cuts through music/calls)
- Repeating urgent tone pattern
- 5-second cooldown between alerts (not continuous annoying)

### International Support
- Auto-detects country from GPS
- Displays speed in local units (mph or km/h)
- Shows country-appropriate speed limit options
- 40+ countries in database

## Target Users
- Everyday drivers wanting speed awareness
- People driving in unfamiliar areas
- Those who've received speeding tickets
- Safety-conscious drivers

## Competitive Landscape
- Waze: Has speed limit display but no alerts in free version
- Google Maps: Shows limits but no alerting
- Dedicated GPS units: Expensive, need separate device
- Speed Alert: Free, simple, focused on one job

