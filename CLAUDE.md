# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
./gradlew :app:assembleDebug              # debug APK
./gradlew :app:testDebugUnitTest          # unit tests (JVM, no device)
./gradlew :app:testDebugUnitTest --tests 'com.eyescare.PostureMathTest'   # one suite
./gradlew :app:installDebug               # install on a connected device
./gradlew :app:connectedDebugAndroidTest  # instrumented Compose tests (needs a device)
./gradlew :app:assembleRelease            # release build with R8
./gradlew :app:lintRelease                # lint — must stay error-free
```

Before claiming work is done, run: `assembleDebug`, `testDebugUnitTest`, `assembleDebugAndroidTest`,
`assembleRelease`, `lintRelease`. The project is kept at **zero compiler warnings and zero lint
errors**; treat a new warning as something to fix, not to ignore.

Gradle re-runs are aggressive about up-to-date checks. Use `:app:cleanTestDebugUnitTest` before
`testDebugUnitTest` when you need to be sure tests actually executed.

## Architecture

Single-Activity Jetpack Compose app. All monitoring lives in a foreground service; the UI only
renders state and sends commands.

**`ForegroundMonitoringService` is the centre of gravity.** It owns `CameraAnalyzer` (CameraX + ML
Kit face detection), the ambient-light and gravity sensors, the overlay, notifications, statistics
and every timer. Almost any behavioural change lands here.

**`MonitoringStateHolder`** is a process-wide `StateFlow` singleton — the only channel from the
service to the UI (live distance, too-close flag, snooze deadline). `SettingsRepository.getInstance`
follows the same process-singleton pattern for persisted settings.

**Pausing is a set of reasons, not a flag.** `MonitoringPause` holds `PauseReason.{SCREEN_OFF,
SNOOZE, SCHEDULE}`; `pause()`/`resume()` return whether the camera should actually be released or
re-acquired, so it happens exactly once regardless of how reasons overlap. Add new reasons here
rather than adding booleans to the service.

**The service never stops while monitoring is on — only the camera is released.** On Android 14+ a
`camera`-typed foreground service cannot be started from the background, so pausing must keep the
service alive. This constraint drives the design of screen-off pausing, snooze and schedule.

**Recovery paths** (`MainActivity.autoResumeMonitoringIfNeeded`, `ResumeWatchWorker`, `BootReceiver`)
all key off `MonitoringStateHolder.state.value.running`. Anything that keeps the service alive while
paused must keep `running == true`, or the watchdogs will fight it.

Compose UI: `MainActivity` → `AppScaffold` (bottom tabs + `NavHost`) → screens. Detail screens live
in `SettingsDetailScreens.kt` and reuse a private `DetailScreen` wrapper, so new ones belong in that
file. The "too close" overlay (`overlay_layout.xml` + `OverlayManager`, WindowManager) is the **only**
XML View left — everything else is Compose.

## Conventions that matter here

**Logic goes in pure classes with no Android imports and no clock.** The caller passes `nowMs`
(`SystemClock.elapsedRealtime()`) and the class returns a decision. Sixteen classes follow this
(`SustainedThreshold`, `MonitoringPause`, `MonitoringSchedule`, `BreakExercise`, `PostureMath`,
`OverlayHysteresis`, `BreakReminder`, `FrameRateGovernor`, `StatsAccumulator`, …), each with a test
suite. If new behaviour is hard to unit-test, the logic is probably still stuck inside the service.

**Soft reminders are built from `SustainedThreshold`** (hysteresis band + dwell + cooldown). Both
`AmbientLightMonitor` and `PostureMonitor` are thin wrappers that only supply domain thresholds.
Direction is inferred from the operand order: `enterAt = 10f, exitAt = 30f` means "trigger when it
drops below 10".

**Sensor sign conventions must be verified on hardware, never assumed.** Unit tests cannot catch a
flipped sign because the test encodes the same assumption. `PostureMath` documents its real device
readings, and `PostureMathTest` pins one measured value as a test case. Until a value is verified,
default the feature off and say in a comment which assumption is unverified.

Debug-only sensor logging is already wired: `adb logcat -s EyesCareSensors` prints `lux`, `tilt`,
`headEulerX`, `flexion` (throttled, shared between the light and posture paths — the posture samples
starve the lux ones, so disable posture when tuning light thresholds).

**`Handler.postDelayed` counts uptime and stalls in deep sleep, while `elapsedRealtime` keeps
running.** Anything scheduled that way must also be re-checked on `ACTION_SCREEN_ON` (see snooze
expiry and the schedule tick).

**Every user-visible string exists in all 7 locales**: `values/` (English, the default) plus
`values-{de,es,fr,it,ru,tr}/`. Use `<plurals>` for anything counted — lint enforces the per-locale
quantity categories, and its list is authoritative over intuition.

**Comments and KDoc are written in Russian and explain *why*, not what.** `README.md` is the
English portfolio-facing showcase; `TECHNICAL_TASK.md` is the dated engineering journal (state,
decisions, audits, backlog, market research). **Read `TECHNICAL_TASK.md` section 1.5 and section 4 at
the start of a session** — it also carries the queue of checks waiting for a physical device.
Record decisions and their reasoning there as part of doing the work.

## Hard constraints

- **No `INTERNET` permission**, deliberately — camera and face data physically cannot leave the
  device. Never add a dependency or feature that needs the network.
- All ML runs on-device (ML Kit, MediaPipe); frames are never stored or transmitted.
- `compileSdk` 37, `targetSdk` 36, `minSdk` 26. `targetSdk` is intentionally behind `compileSdk`:
  raising it changes runtime behaviour and requires device verification.
- Release is currently signed with the debug key for internal builds; `docs/RELEASE.md` holds the
  publication checklist.
- The MediaPipe iris path is a debug-only accuracy prototype (`BuildConfig.DEBUG`), running beside
  the ML Kit IPD path without affecting monitoring.
