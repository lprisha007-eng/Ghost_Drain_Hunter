# 👻 Ghost Drain Hunter (DBC)

An Android app that catches **"ghost drain"**: battery that quietly disappears while your phone sits in your pocket with the screen off.

Built for the **Daemon Hour hackathon** by **team DBC**.

---

## What it does

Ghost Drain Hunter runs a lightweight foreground service that watches your screen on/off events. Every time the screen turns off, it records the battery level. When the screen turns back on, it records it again and works out how fast the battery drained while idle.

From those sessions, the app gives you:

- **Ghost Drain Score (0-100)**: a single number for how healthy your idle drain is.
- **Smart Ghost Alerts**: a notification when a screen-off session drains faster than your limit.
- **Screen-Off History**: every session with start/end battery, duration, rate and verdict, with filters (All, Valid, Too Short, Charging, Flagged only) and sorting (Date, Rate).
- **Drain Pattern**: finds the 2-hour window of the day when your idle drain is highest.
- **Trends**: today vs yesterday, this week vs last week, and a 7-day daily drain chart.
- **PDF Battery Report**: export or share a 7-day or 30-day report.
- **Settings**: change the alert threshold and minimum session length, toggle alerts, or "Start fresh" to wipe all data.

All data stays **on your device**. Nothing is uploaded.

---

## How the score works

```
Score = round( clamp(100 - 10 × avgRate, 0, 100) )
```

`avgRate` is the weighted average drain in %/hr across valid sessions from the last 7 days.

| Score | Label |
|-------|-------|
| 80-100 | Low idle drain |
| 60-79 | Moderate idle drain |
| 40-59 | High idle drain |
| 0-39 | Very high idle drain |

The score only appears once there is enough data: **at least 3 valid sessions and 4 hours of idle time** in the last 7 days.

### What counts as a valid session?

A session is **excluded** if:

- the charger was connected at any point,
- it is shorter than the minimum length (default 15 min),
- or the battery percentage went up or the data is invalid.

A valid session is **flagged** when its rate is above your threshold (default **3 %/hr**). Verdicts are *Within limit*, *Above limit* (up to 2× the threshold) and *Well above limit* (more than 2×).

> This is a local heuristic based on measured idle drain. It is not an official battery-health measurement.

---

## Tech stack

| Area | Tech |
|------|------|
| Language | Kotlin |
| UI | Jetpack Compose + Material 3 |
| Local database | Room (with KSP) |
| Settings | Jetpack DataStore (Preferences) |
| Background work | Foreground service + boot receiver |
| Architecture | ViewModel + simple `AppContainer` for dependencies |
| Testing | JUnit unit tests |

**SDK:** min SDK 24, target/compile SDK 36, Java 17.

---

## Project structure

```
app/src/main/java/com/example/ghostdrainhunter/
├── MainActivity.kt            # Entry point and tab navigation
├── GhostDrainApplication.kt
├── AppContainer.kt            # Dependency setup
├── data/
│   ├── capture/               # CaptureService, BootReceiver, BatteryReader
│   ├── db/                    # Room: AppDatabase, ScreenOffSession, DAO
│   ├── settings/              # SettingsStore (DataStore)
│   └── debug/                 # DebugSeeder (sample data)
├── domain/                    # Core logic
│   ├── SessionAnalyzer.kt
│   ├── GhostScoreCalculator.kt
│   ├── PatternDetector.kt
│   ├── TrendCalculator.kt
│   ├── TimeAllocator.kt
│   └── DrainAlertNotifier.kt
├── report/                    # ReportBuilder, PdfReportWriter
└── ui/                        # Dashboard, History, Trends, Report,
                               # Settings, Onboarding, Theme
```

---

## Getting started

### Requirements

- Android Studio (latest stable)
- JDK 17
- An Android device or emulator running Android 7.0 (API 24) or higher

### Run it

1. Clone the repo:
   ```bash
   git clone https://github.com/lprisha007-eng/Ghost_Drain_Hunter.git
   ```
2. Open the folder in **Android Studio** and let Gradle sync.
3. Select a device and press **Run ▶**.
4. On first launch, allow **notifications** and tap **Start Monitoring**.
5. Turn your screen off for a while, then check back.

### Run the tests

```bash
./gradlew test
```

On Windows use `gradlew.bat test`.

---

## Permissions

| Permission | Why |
|------------|-----|
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_SPECIAL_USE` | Keep monitoring screen-off battery drain |
| `RECEIVE_BOOT_COMPLETED` | Resume monitoring after a reboot or app update |
| `POST_NOTIFICATIONS` | Show the monitoring notice and drain alerts |

---

## Team DBC

Built at the Daemon Hour hackathon.

- *Prisha*

