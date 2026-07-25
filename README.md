# MockLocation

**MockLocation** is an Android app that simulates GPS movement along a planned
route. It uses Android's **mock location app** developer option to inject
realistic GPS data into the system, making any app on the device believe you
are moving along the chosen path.

Select two points on the map, calculate a driving route via
[OSRM](https://project-osrm.org/), then press *Play* to watch your GPS
position follow the route in real time at a configurable speed.

---

## Features

- **OpenStreetMap** — Full interactive map with no API keys required (powered
  by osmdroid)
- **Route planning** — Pick start and end points with a long-press; route is
  automatically calculated via OSRM's public routing API
- **GPS simulation** — Injects mock locations through the system's
  `LocationManager`, following the route point by point
- **Configurable speed** — 0.5×, 1×, 2×, 5×, 10×, and 50× multipliers
  relative to the route's realistic driving speed
- **Playback controls** — Play, Pause, and Stop with live progress tracking
- **Simulation overlay** — Real-time progress percentage, speed, segment
  counter, and progress bar while the simulation runs
- **Error guidance** — Clear on-screen messages when mock location permission
  has not been granted

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

> ⚠️ Without this step the app will show an error and the simulation will not
> be able to inject GPS data.

---

## Usage

1. **Open MockLocation** — the map centres on Rome by default; zoom and pan to
   your area of interest.

2. **Set the start point** — Long-press anywhere on the map. The chip at the
   bottom reads *"Set START"* and a green marker appears.

3. **Set the end point** — Long-press a second location. The chip now reads
   *"Set END"* and a red marker appears. The route is fetched automatically.

4. **Play the simulation** — Tap **▶ Play** in the bottom bar. The blue marker
   moves along the route and GPS coordinates are injected into the system at
   every tick.

5. **Adjust speed** — Tap a speed chip (e.g. `5×`) below the Play button to
   go faster or slower in real time.

6. **Pause / Resume / Stop** — Use the playback buttons to control the
   simulation at any time.

7. **Clear** — Tap **Clear** to remove all markers and the route.

> 💡 While the simulation is running, any app on your device that reads GPS
> location will see the simulated position — navigation apps, map apps,
> location-based games, etc.

---

## Build from source

### Prerequisites (macOS)

- **JDK 21** (Java 26 is not yet supported by the Kotlin compiler)
  ```bash
  brew install openjdk@21
  ```
- **Android SDK** (API 35 recommended)
- Set `ANDROID_HOME` or create a `local.properties` file:
  ```properties
  sdk.dir=/Users/youruser/Library/Android/sdk
  ```

### Build

```bash
# Clone the repository
git clone https://github.com/vincenzobpt/MockLocation.git
cd MockLocation

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
| UI | **Jetpack Compose** + Material 3 |
| Map | **osmdroid** 6.1 (OpenStreetMap tiles) |
| Routing | **OSRM** public API via Retrofit 2 |
| Architecture | **MVVM** — single-activity, AndroidViewModel + StateFlow |
| Build | **Gradle** 8.11 with AGP 8.7 |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 35 (Android 15) |

---

## Project structure

```
MockLocation/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/mocklocation/app/
│       │   ├── MainActivity.kt          # Single-activity entry point
│       │   ├── MockLocationApp.kt       # Application class (osmdroid config)
│       │   ├── model/                   # Data classes
│       │   │   ├── LatLng.kt            # Geographic coordinate + helpers
│       │   │   ├── MarkerMode.kt        # START / END enum
│       │   │   ├── Route.kt             # Route with distance & duration
│       │   │   └── SimulationState.kt   # Playback state machine
│       │   ├── network/
│       │   │   ├── OsrmRepository.kt    # OSRM API client
│       │   │   └── OsrmResponse.kt      # API response DTOs
│       │   ├── viewmodel/
│       │   │   └── MapViewModel.kt      # State + simulation engine
│       │   └── ui/
│       │       ├── MapScreen.kt         # Main Compose screen
│       │       └── theme/Theme.kt       # Material3 color scheme
│       └── res/                         # Resources, icons, config
├── gradle/
│   └── libs.versions.toml               # Version catalog
├── build.gradle.kts                     # Root build script
├── build.sh                             # Convenience build script
├── settings.gradle.kts
└── gradlew / gradlew.bat                # Gradle wrapper
```

---

## How it works

### Mock location injection

When *Play* is pressed, the `MapViewModel` starts a coroutine that iterates
through each segment of the route polyline. For each step it:

1. Interpolates the position between the two endpoints of the segment
2. Computes realistic speed based on OSRM's estimated duration
3. Calls `LocationManager.setTestProviderLocation()` with the computed
   `Location` object (including latitude, longitude, bearing, speed, and
   accuracy)
4. Waits for the configured interval (1 s ÷ speed multiplier, min 50 ms)

The system propagates this location to all apps listening for GPS updates.

### Route calculation

When both markers are placed, `OsrmRepository` sends a request to
`https://router.project-osrm.org/route/v1/driving/{lng1},{lat1};{lng2},{lat2}`
with `geometries=geojson`. The returned polyline is decoded and stored as a
list of `LatLng` waypoints.

---

## License & Attribution

This project is licensed under the **MIT License** — see [LICENSE](LICENSE).

**By using or distributing this code, including modified versions or forks,
you agree to:**

- Retain the original copyright notice
- Retain the original repository URL (`https://github.com/vincenzobpt/MockLocation`)
  in a visible place (e.g. `LICENSE`, `README`, or the app's About screen)
- Clearly indicate what changes were made if you distribute a modified version

These requirements are part of the license terms and apply to all copies and
derivative works.

---

## Author

**Vincenzo Buonomano** — [@vincenzobpt](https://github.com/vincenzobpt)
