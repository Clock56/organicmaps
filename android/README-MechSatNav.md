# MechSatNav – Organic Maps Telemetry Fork

## Base

Upstream project: https://github.com/organicmaps/organicmaps  
Fork base commit: `de75f88219`  
Branch: `telemetry-salvage`

This fork adds external telemetry output for a custom hardware navigation
cluster ("MechSatNav") while preserving Organic Maps routing behaviour.

Routing logic, route building, and navigation algorithms remain unmodified.

---

# Purpose

This fork exposes structured navigation telemetry over UDP for use by
external hardware (ESP32-based dual-dial mechanical cluster).

The Android application remains fully functional and stable as a standalone
navigation app.

No routing engine behaviour is altered.

---

# Architectural Constraints

The following design rules were enforced:

- No ShadowRouter
- No asynchronous JNI calls
- No background polling of native code
- No modification of routing engine logic
- All telemetry extracted from existing routing update flow
- UI thread safe
- Release build stable

Telemetry is sourced from:

`MwmActivity.onLocationUpdated(...)`

which already retrieves `RoutingInfo`.

---

# Modified Files

## Android Layer

### `app/src/main/java/app/organicmaps/MwmActivity.java`
- Hooked telemetry updates into `onLocationUpdated`
- Added calls to:
  - `TelemetryTicker.setRoutingData(...)`
  - `TelemetryTicker.setSpeedCameraData(...)`

---

### `app/src/main/java/app/organicmaps/util/telemetry/TelemetryTicker.kt`
- Main-thread telemetry ticker (2500ms interval)
- JSON schema v1 generation
- UDP transmission
- Turn graphic metadata integration
- Speed camera dataset integration
- Camera active derived from distance validity

---

## JNI / Native Layer

### `sdk/src/main/cpp/app/organicmaps/sdk/routing/RoutingInfo.hpp`
Extended `CreateRoutingInfo(...)` to expose:

- `speedCamDistanceMeters`
- `speedCamSpeedKmph`

Camera distance computed as:

```
camera.m_distFromBeginMeters
    - route->GetCurrentDistanceFromBeginMeters()
```

Speed value filtered to ignore `kNoSpeedInfo (255)`.

Routing engine behaviour unchanged.

---

### `sdk/src/main/java/app/organicmaps/sdk/routing/RoutingInfo.java`
Constructor extended to include:

- `double speedCamDistanceMeters`
- `double speedCamSpeedKmph`

Getters added:

```
getSpeedCamDistanceMeters()
getSpeedCamSpeedKmph()
```

---

# Telemetry JSON Schema (v1)

Example structure:

```json
{
  "meta": {
    "version": 1,
    "timestamp_ms": 0,
    "app_alive": true,
    "nav_active": true
  },

  "vehicle": {
    "speed_mps": 13.9,
    "heading_deg": 54,
    "latitude_deg": 52.15,
    "longitude_deg": -2.33,
    "speed_limit_mps": 17.7
  },

  "navigation": {
    "distance_to_turn_m": 3400,
    "time_to_turn_s": 245,
    "turn_type": "EnterRoundAbout",
    "roundabout_exit": 4,
    "current_road": "[A4103]",
    "next_road": null,
    "distance_to_destination_m": 3500,
    "time_to_destination_s": 228,
    "eta_epoch_s": 1771010821
  },

  "turn_graphic": {
    "present": true,
    "uid": "EnterRoundAbout_exit_4",
    "format": "png",
    "width": 128,
    "height": 128
  },

  "alerts": {
    "speed_camera": {
      "active": true,
      "distance_m": 245,
      "time_s": 6,
      "speed_limit_mps": null,
      "speed_exceeded": false
    }
  }
}
```

---

# Speed Camera Behaviour

- `active` = camera distance valid (not beep-zone dependent)
- `distance_m` = routing-engine authoritative
- `time_s` = distance / current vehicle speed
- `speed_limit_mps` = null if camera has no speed metadata
- `speed_exceeded` = native routing engine value

If camera speed metadata is absent in OSM,
`speed_limit_mps` will be null.

---

# Phase 1 Scope (Complete)

- Routing telemetry
- Turn identity
- Turn graphic UID transmission
- PNG turn graphic UDP transmission
- Speed camera distance exposure
- Speed camera metadata exposure
- No routing engine modifications

Phase 1 is considered stable.

---

# Future Phases

Planned future work (not included here):

- Road look-ahead telemetry
- Non-routing curvature prediction
- ESP32 hardware integration refinement
- Schema v2 expansion

