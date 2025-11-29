# Project Brief: Speed/Limit

## Overview
Speed/Limit is an Android native application that monitors driving speed and alerts users when they exceed posted speed limits. The app runs in the background, provides audible/visual alerts, and offers a floating overlay for minimal distraction while using other apps.

## Core Requirements

### Primary Function
- Monitor GPS speed continuously in background
- Lookup speed limits from OpenStreetMap via Overpass API
- Alert when current speed exceeds limit by more than 5%
- Display current speed and detected speed limit
- Floating overlay mode for use with other apps

### Technical Requirements
- Android native app using Kotlin
- Minimum SDK: API 26 (Android 8.0)
- Target SDK: API 35 (Android 15)
- Foreground services for background operation
- No external API keys required for core functionality (uses free OSM data)
- Package name: `com.speedlimit`

### Legal Requirements
- Disclaimer shown every app launch
- 85 language translations for global coverage
- Clear liability waiver
- Safety warnings
- Accept/Decline flow

### User Experience Goals
- Minimalist black/white UI for glare-free driving visibility
- Large, readable speed display
- Unmissable alerts (loud audio + vibration + visual flash)
- Works globally with automatic unit detection (mph/km/h)
- Floating overlay that auto-shows when speeding
- Tappable speed limit grid for future crowdsourcing

## Project Scope

### Completed (POC)
- GPS speed monitoring
- OSM speed limit lookups
- Audio/vibration alerts
- Country-aware unit display
- Dynamic speed limit grid per country
- Floating overlay mode
- Legal disclaimer (85 languages)
- Auto-show floating on alert

### Future Scope
- Crowdsourcing speed limit contributions back to OSM
- Offline speed limit caching
- User preference overrides
- Historical trip data
- Custom app icon
- Play Store listing

## Success Criteria
- App runs reliably in background ✅
- Speed limits detected for roads with OSM data ✅
- Alerts trigger correctly when speeding ✅
- Works in any country with correct units ✅
- Legal protection via disclaimer ✅
- Floating overlay works over other apps ✅

## Repository
- GitHub: itomicspaceman/speed-limit
- Local: C:\Users\Ross Gerring\Herd\speed
- Current Version: 2.4
