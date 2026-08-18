# MockLocation

**MockLocation** is an Android app that simulates GPS movement along a planned
route, with intermediate stops. It uses Android's **mock location app**
developer option to inject realistic GPS data into the system, making any app
on the device believe you are moving along the chosen path.

Long-press the map to place a start, an end, and as many stops in between as
you like; the route is calculated via [OSRM](https://project-osrm.org/), and
pressing **DRIVE** starts a physically-integrated simulation — real
acceleration and braking, corner speed limits from the route's own geometry,
and a receiver-like GPS trace — that keeps injecting fixes even while you
switch to the app you're actually testing.

---

## Features

- **OpenStreetMap** — Full interactive map with no API keys required (powered
  by osmdroid), with a dark, light, or system-following basemap
- **Route planning** — Long-press to place start, end, and intermediate stops
  in one continuous sequence; the route is recalculated through all of them
  via OSRM's public routing API
- **Intermediate stops** — Each stop has its own dwell time; the simulation
  halts there, keeps injecting a stationary fix for the duration (a receiver
  that goes silent while parked isn't a realistic one), then continues
- **Physical simulation** — A proper time integration (not a segment-by-segment
  walk): real acceleration/braking curves, corner speed limits derived from
  the route's own curvature, and a coast-to-a-stop arrival at the next stop or
  the destination
- **Runs in the background** — A foreground service keeps the simulation
  injecting fixes while you switch to the app you're testing; a notification
  shows progress with pause/stop actions
- **GPS receiver realism** — Configurable fix rate (1–10 Hz), sub-metre GPS
  jitter, wandering satellite count and HDOP, and terrain-following altitude
  from OSRM elevation data (or a synthetic profile when none is available)
- **Telemetry console** — Live speed gauge, heading compass, DMS coordinates,
  accuracy/HDOP/satellite readout, and an elevation profile, alongside the
  route and tuning controls
- **Playback controls** — Play, Pause, and Stop, plus scrubbing the progress
  rail to seek anywhere along the route
- **Clear failure states** — Missing runtime permission and "not the selected
  mock location app" are surfaced as an actionable banner, not a silent no-op

---

## Screenshots

*(Add screenshots here once published.)*

---

## Prerequisites

- Android device running **Android 8.0 (API 26)** or later
- **Developer Options** enabled on the device
- USB connection (or ADB over Wi-Fi) for initial installation

---

## Setup

### 1. Enable Developer Options

1. Open **Settings → About phone**
2. Tap **Build number** 7 times
3. Go back to **Settings → System → Developer options**

### 2. Install the app

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

### 3. Grant mock location permission

1. Open **Settings → System → Developer options**
2. Scroll to **Select mock location app**
3. Tap and choose **MockLocation**
4. On first launch, the app also asks for precise location at runtime — grant
   it; a manifest declaration alone hasn't been enough to inject fixes since
   Android 6.

> ⚠️ Both steps are required. Missing either one shows an actionable banner
> in the app rather than silently doing nothing.

---

## Usage

1. **Open MockLocation** — the map centres on Rome by default; zoom and pan to
   your area of interest.

2. **Place your points** — Long-press the map. The first press sets **START**,
   the second **END**, and every press after that adds a numbered **STOP** —
   the chip at the top always shows what the next press will place. Tap the
   chip to cycle the mode manually.

3. **Tune each stop** — Open the console (drag it up) and switch to the
   **ROUTE** tab: each stop lists its coordinates and a tappable dwell preset
   (pass-through, 5 s … 5 min). The route recalculates through every stop in
   order automatically.

4. **Drive** — Tap **DRIVE**. The vehicle eases up to speed, brakes for
   corners and for the next stop or the destination, and holds position for
   the full dwell time at each stop while still injecting fixes.

5. **Watch the telemetry** — The **TELEMETRY** tab has a live speed gauge,
   heading, altitude, DMS coordinates, and GPS receiver stats (accuracy,
   HDOP, satellites, fix rate).

6. **Tune the simulation** — The **TUNING** tab covers the basemap
   (light/dark/system), speed and altitude bands, fix rate, GPS jitter, corner
   braking, and route looping.

7. **Pause / seek / stop** — Use the transport bar, or drag the progress rail
   to jump anywhere along the route (stops show as ticks on the rail).

8. **Clear** — Tap ✕ to remove all points, stops, and the route.

> 💡 While the simulation is running, any app on your device that reads GPS
> location will see the simulated position — navigation apps, map apps,
> location-based games, etc. A foreground service keeps it running while you
> switch away from MockLocation to check.

---

## Build from source

### Prerequisites (macOS)

- **JDK 17 or 21** — Gradle 8.11 does not yet support newer JDKs (a JDK 26
  install, for instance, fails outright)
  ```bash
  brew install openjdk@21   # or openjdk@17
  ```
- **Android SDK** (API 35 recommended)
- Set `ANDROID_HOME` or create a `local.properties` file:
  ```properties
  sdk.dir=/Users/youruser/Library/Android/sdk
  ```

### Build

```bash
# Clone the repository
git clone https://github.com/vincenzobpt/gps-mock-location.git
cd gps-mock-location

# Build the debug APK
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
  ./gradlew assembleDebug

# Or use the convenience script
./build.sh
```

The APK is written to:

```
app/build/outputs/apk/debug/app-debug.apk
```

---

## Tech stack

| Layer | Technology |
|---|---|
| Language | **Kotlin** 2.1.x |
| UI | **Jetpack Compose** + Material 3, custom osmdroid overlays for the route/vehicle/pins |
| Map | **osmdroid** 6.1 — OpenStreetMap (light) and CARTO Dark Matter (dark) tiles |
| Routing | **OSRM** public API via Retrofit 2, arbitrary number of waypoints per request |
| Architecture | **MVVM** — single-activity, AndroidViewModel + StateFlow, with the simulation clock owned by the `Application` (not the ViewModel) so a run survives leaving the screen |
| Persistence | `SharedPreferences` for the basemap choice |
| Testing | **JUnit 4**, plain JVM unit tests — the mock-location backend is reached through an interface (`MockLocationPort`) so the engine's integrator and threading are tested against a fake, no device or emulator required |
| Build | **Gradle** 8.11 with AGP 8.7 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |

---

## Project structure

```
gps-mock-location/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/mocklocation/app/
│       │   │   ├── MainActivity.kt          # Single-activity entry point
│       │   │   ├── MockLocationApp.kt       # Application class — owns the SimulationEngine, configures osmdroid
│       │   │   ├── data/
│       │   │   │   └── Preferences.kt       # Basemap preference persistence
│       │   │   ├── location/
│       │   │   │   ├── MockLocationEngine.kt   # Owns the platform test-location providers
│       │   │   │   └── MockLocationPort.kt     # The interface SimulationEngine depends on (fakeable in tests)
│       │   │   ├── model/                   # LatLng, Route, Waypoint, MarkerMode,
│       │   │   │                             # SimulationState, SimulationConfig, MapTheme, …
│       │   │   ├── network/
│       │   │   │   ├── OsrmRepository.kt    # OSRM API client
│       │   │   │   └── OsrmResponse.kt      # API response DTOs
│       │   │   ├── service/
│       │   │   │   └── MockLocationService.kt  # Foreground service — keeps injecting while backgrounded
│       │   │   ├── simulation/
│       │   │   │   ├── SimulationEngine.kt  # The tick loop: time integration, stops, thread-safe transport
│       │   │   │   └── RouteGeometry.kt     # Arc-length parameterisation, curvature, nearest-point snapping
│       │   │   ├── viewmodel/
│       │   │   │   └── MapViewModel.kt      # Map/route/search UI state; forwards transport calls to the engine
│       │   │   └── ui/
│       │   │       ├── MapScreen.kt         # Top-level screen layout
│       │   │       ├── MapCanvas.kt         # osmdroid + custom route/vehicle/pin overlays
│       │   │       ├── Console.kt           # Bottom sheet: Route / Telemetry / Tuning tabs
│       │   │       ├── Instruments.kt       # Speed gauge, compass, elevation chart, progress rail
│       │   │       ├── Common.kt            # Shared glass/pill/readout primitives
│       │   │       ├── AboutDialog.kt
│       │   │       └── theme/Theme.kt       # Material3 color scheme
│       │   └── res/                         # Resources, icons, config
│       └── test/java/com/mocklocation/app/  # Plain JVM unit tests (see Testing, below)
├── gradle/
│   └── libs.versions.toml               # Version catalog
├── build.gradle.kts                     # Root build script
├── build.sh                             # Convenience build script
├── settings.gradle.kts
└── gradlew / gradlew.bat                # Gradle wrapper
```

---

## How it works

### Route calculation

When the start, end, and any stops are placed, `OsrmRepository` sends a
single request to
`https://router.project-osrm.org/route/v1/driving/{lng1},{lat1};{lng2},{lat2};…`
— OSRM accepts any number of coordinates, so routing through stops costs
nothing extra. The returned polyline is decoded into a `Route`.

### Simulation

`RouteGeometry` turns the route into an arc-length parameterisation: a single
scalar (metres travelled) maps to a position, heading, and a curvature-derived
speed ceiling. `SimulationEngine` advances that scalar with a real time
integration each tick (`distance += speed * dt`, with asymmetric
acceleration/braking and an arrival ceiling that coasts to a stop rather than
snapping to it), rather than stepping through route segments — which is what
makes the motion physically consistent regardless of tick rate or mid-route
speed changes.

Each waypoint is snapped onto the route by arc length, so a stop triggers
where the route actually passes it. Reaching one holds the simulation there —
still injecting stationary fixes — for its configured dwell time before
continuing.

The engine's integrator and every write to its published state are guarded by
a single lock, because transport commands (play/pause/stop/seek) arrive from
the UI thread while the tick loop runs on a background dispatcher; a run
epoch bumped on every transport transition stops a tick from publishing after
the command that ended it.

### Mock location injection

`MockLocationEngine` registers Android's test location providers — `gps`,
`network`, and (from Android 12) `fused` — **once per run**, not once per fix:
re-registering on every tick resets each provider's state and consumers never
see a stable position. Every tick then pushes one `Location` (latitude,
longitude, altitude, bearing, speed, accuracy, satellite count) to each
registered provider via `LocationManager.setTestProviderLocation()`, and the
system propagates it to every app listening for GPS updates.

---

## Testing

The simulation's tick loop, its stop handling, and its transport threading are
covered by plain JVM unit tests — no Robolectric, no device, no emulator.
`SimulationEngine` reaches its mock-location backend through the
`MockLocationPort` interface, so tests drive it with an in-memory fake instead
of the real platform providers.

```bash
./gradlew testDebugUnitTest
```

Coverage includes: motion beginning on the very first tick, editing a stop's
dwell time without interrupting a run in progress, a provider that vanishes
mid-run correctly pausing and surfacing why, `seekTo` landing exactly on the
requested fraction, and — the two tests that exist specifically to guard the
locking — rapid play/pause cycling and concurrent transport commands from
multiple threads always ending in a coherent state.

---

## License & Attribution

This project is licensed under the **MIT License** — see [LICENSE](LICENSE).

**By using or distributing this code, including modified versions or forks,
you agree to:**

- Retain the original copyright notice
- Retain the original repository URL (`https://github.com/vincenzobpt/gps-mock-location`)
  in a visible place (e.g. `LICENSE`, `README`, or the app's About screen)
- Clearly indicate what changes were made if you distribute a modified version

These requirements are part of the license terms and apply to all copies and
derivative works.

---

## Author

**Vincenzo Buonomano** — [@vincenzobpt](https://github.com/vincenzobpt)
