# Speed/Limit Premium Features Roadmap

## Overview

This document outlines all premium features planned for Speed/Limit subscribers. Features are grouped by category and prioritized by implementation complexity and user value.

---

## 🎯 Subscription Model

- **Pricing**: Regional pricing via Google Play (cheaper in lower-income countries)
- **Free Tier**: Core functionality with ads after disclaimer
- **Premium Tier**: All features, no ads, unlimited contributions

---

## 📋 Feature Categories

### 1. 🔊 Voice Announcements (HIGH PRIORITY)
**Status**: Settings UI exists, TTS engine integrated, needs full implementation

| Feature | Description | Complexity |
|---------|-------------|------------|
| Speed limit change | "Speed limit is now 40" when entering new zone | Low |
| Unknown zone warning | "Speed limit unknown" when no data available | Low |
| Over limit alert | "You are over the speed limit" when speeding | Low |
| Custom voice selection | Choose from available TTS voices | Medium |
| Announcement language | Override system language for announcements | Medium |

**Technical**: Uses Android TextToSpeech API - auto-translates to device language.

---

### 2. 🎨 Customization & Personalization (MEDIUM PRIORITY)

| Feature | Description | Complexity |
|---------|-------------|------------|
| Font selection | Choose from 5-10 curated fonts for speed display | Medium |
| Floating overlay layout | Horizontal (41/30) vs Vertical (41 over 30) display | Low |
| Color themes | Alternative color schemes (e.g., night red, high contrast) | Medium |
| Alert sounds | Choose from multiple alert tones | Low |
| Vibration patterns | Customize vibration intensity/pattern | Low |

---

### 3. 📊 Advanced Features (MEDIUM PRIORITY)

| Feature | Description | Complexity |
|---------|-------------|------------|
| Speed history/log | View your speed history over time | Medium |
| Trip statistics | Distance, avg speed, time over limit per trip | Medium |
| Speed limit map | View nearby speed limits on a map | High |
| Offline mode | Download speed limit data for offline use | High |
| Widget | Home screen widget showing current speed | Medium |

---

### 4. 🗺️ OSM Contribution Enhancements (LOW PRIORITY)

| Feature | Description | Complexity |
|---------|-------------|------------|
| Unlimited contributions | Free users: 10/day, Premium: unlimited | Low |
| Contribution statistics | Detailed stats, badges, milestones | Medium |
| Priority submission | Faster processing for premium contributors | Low |
| Contribution history sync | Cloud backup of contribution history | Medium |

---

### 5. 🚗 Driving Enhancements (FUTURE)

| Feature | Description | Complexity |
|---------|-------------|------------|
| Speed camera alerts | Warn of upcoming speed cameras | High |
| School zone alerts | Time-based school zone warnings | High |
| Route planning integration | Show speed limits along planned route | Very High |
| CarPlay / Android Auto | Dashboard integration | Very High |

---

## 🚀 Implementation Priority

### Phase 1 (MVP Premium - Target: v4.0)
1. ✅ Voice announcements (limit change, unknown, over limit)
2. ✅ Floating overlay layout toggle (horizontal/vertical)
3. ✅ Remove ads for subscribers
4. 🔲 Unlimited OSM contributions

### Phase 2 (Enhanced Premium - Target: v4.5)
1. 🔲 Font selection
2. 🔲 Color themes
3. 🔲 Custom alert sounds
4. 🔲 Vibration patterns

### Phase 3 (Advanced Premium - Target: v5.0)
1. 🔲 Trip statistics
2. 🔲 Speed history/log
3. 🔲 Home screen widget
4. 🔲 Contribution badges/milestones

### Phase 4 (Future Expansion)
1. 🔲 Offline mode
2. 🔲 Speed limit map view
3. 🔲 Speed camera alerts
4. 🔲 CarPlay / Android Auto

---

## 💰 Monetization Strategy

### Free Tier Includes:
- Core speed monitoring
- Speed limit detection
- Visual & audio alerts
- Floating overlay (horizontal only)
- OSM contributions (limited to 10/day)
- Ads shown after disclaimer acceptance

### Premium Tier Includes:
- Everything in Free
- **No ads**
- Voice announcements (all triggers)
- Layout customization
- Font & theme selection
- Unlimited OSM contributions
- Future premium features as released

---

## 📱 Settings Screen Structure

```
⚙️ SETTINGS

FREE FEATURES
├── 🗺️ OpenStreetMap Account → [Connected/Not Connected]
├── 🎯 My Contributions → [View log]
└── 🎓 Show Tour Again

─────────────────────────

PREMIUM FEATURES 👑
├── 🔊 Voice Announcements [Toggle]
│   ├── Announce limit changes [Toggle]
│   ├── Announce unknown zones [Toggle]
│   └── Announce when over limit [Toggle]
├── 🎨 Display Layout [Horizontal/Vertical] 🔒
├── 🔤 Font Style [Selection] 🔒
├── 🎨 Color Theme [Selection] 🔒
└── 🔔 Alert Sound [Selection] 🔒

─────────────────────────

[✨ Unlock Premium - $X.XX/month]
[Restore Purchases]

─────────────────────────

Made with ❤️ by Itomic Digital
```

*🔒 = Locked for non-subscribers (visible but disabled, shows upgrade prompt on tap)*

---

## 📝 Notes

- All premium features should be **visible** to free users but locked
- Tapping a locked feature shows a friendly upgrade prompt
- Voice features use Android TTS (free, works offline, auto-translates)
- No server costs for most premium features (all client-side)
- Consider annual subscription discount (e.g., 2 months free)

---

*Last updated: November 2024*

