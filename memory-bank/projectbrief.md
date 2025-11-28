# Project Brief: Speed Alert

## Overview
Speed Alert is an Android native application that monitors driving speed and alerts users when they exceed posted speed limits. The app runs in the background and provides audible/visual alerts to promote safer driving.

## Core Requirements

### Primary Function
- Monitor GPS speed continuously in background
- Lookup speed limits from OpenStreetMap via Overpass API
- Alert when current speed exceeds limit by more than 5%
- Display current speed and detected speed limit

### Technical Requirements
- Android native app using Kotlin
- Minimum SDK: API 26 (Android 8.0)
- Target SDK: API 35 (Android 15)
- Foreground service for background operation
- No external API keys required (uses free OSM data)

### User Experience Goals
- Minimalist black/white UI for glare-free driving visibility
- Large, readable speed display
- Unmissable alerts (loud audio + vibration + visual flash)
- Works globally with automatic unit detection (mph/km/h)
- Tappable speed limit grid for future crowdsourcing

## Project Scope

### In Scope (POC)
- GPS speed monitoring
- OSM speed limit lookups
- Audio/vibration alerts
- Country-aware unit display
- Dynamic speed limit grid per country

### Future Scope
- Crowdsourcing speed limit contributions back to OSM
- Offline speed limit caching
- User preference overrides
- Historical trip data

## Success Criteria
- App runs reliably in background
- Speed limits detected for roads with OSM data
- Alerts trigger correctly when speeding
- Works in any country with correct units

## Repository
- GitHub: itomicspaceman/speed-alert
- Local: C:\Users\Ross Gerring\Herd\speed

