# EyesCare — screen-distance monitor for healthier eyes

**EyesCare** is a production-ready Android app that uses the front camera and on-device ML to measure
how far your eyes are from the screen, and gently warns you when you get too close — a small tool
against digital eye strain and the rising tide of childhood myopia. Everything runs **locally on the
device**: no internet permission, no accounts, no data ever leaves the phone.

---

## About this project

EyesCare is a real, shippable app. It is also a deliberate experiment.

I'm a backend engineer (Go), tech lead and project manager. **I did not write a single line of this
app's code** — every line was produced by an AI agent (Claude Code) under my direction. I deliberately
picked **Android — a stack outside my expertise** — to test a thesis:

> **As AI makes writing code cheap, the scarce input becomes professional judgment.
> A junior + AI ships something that runs; a professional + AI ships something production-grade —
> because the professional already knows *what* to build, *how* to run the project, and *where*
> quality actually lives.**

What I brought wasn't Android knowledge, but the transferable layer: running an IT project end-to-end,
scoping and sequencing, enforcing quality, and knowing what the user actually needs. The AI supplied
the Android-specific execution. I directed and reviewed it — the architecture, product, quality and
release decisions are mine.

**The evidence is in the repo, not the claim** (see [`TECHNICAL_TASK.md`](TECHNICAL_TASK.md) — the full
dated engineering journal):

- a **dated decision journal** — what was tried, kept, reverted, and *why*;
- **security, memory and performance audits**, with fixes;
- a **release-readiness checklist** and repeated **on-device verification**;
- deliberate **product trade-offs** (why not ARCore, why no ads, privacy-by-design with **no `INTERNET`
  permission**);
- a **market & monetization analysis** made *before* deciding how — and whether — to ship commercially.

This is my first fully AI-built project: a step toward working as a **T-shaped, AI-native
professional** — deep roots in backend and delivery leadership, deliberately broadened by using AI to
operate confidently outside my home stack, as an **Agentic Engineering Lead / Product Systems Lead**.

---

## What it does

- **Real-time distance measurement** — front camera + ML Kit face detection; distance derived from
  interpupillary distance and the camera's physical parameters, corrected for head tilt.
- **Full-screen "too close" warning** — an overlay banner over any app (video, browser, games), with a
  vibration and an emergency-stop button; appears/disappears with hysteresis so it never flickers.
- **Runs in the background** — a foreground service keeps monitoring while you use other apps.
- **Calibration** — automatic (fit your face in the oval) or manual interpupillary-distance entry.
- **Eye-care habits** — 20-20-20 break reminders and a weekly usage summary.
- **Dark-room warning** — the ambient-light sensor (not the camera) notices when you're staring at a
  bright screen in the dark, with hysteresis, a dwell time and a cooldown so it never nags.
- **Posture reminder** — head tilt from ML Kit combined with device tilt from the gravity sensor
  gives the real neck angle, so it also catches the phone-flat-on-the-desk case a face angle alone
  would miss.
- **Pause on demand** — snooze monitoring for 15/30/60 minutes from the app or straight from the
  notification, for legitimate close-up viewing; the service stays alive and only releases the camera.
- **Child mode** — a more conservative threshold for kids.
- **Battery-aware & resilient** — dynamic frame rate, camera released when the screen is off,
  auto-off on low battery, auto-resume after reboot / OEM kills.
- **Accuracy R&D** — a debug-only prototype measuring distance from **iris diameter** (MediaPipe Face
  Landmarker) as a calibration-free alternative, pending on-device accuracy comparison.

## Privacy by design

- **No `INTERNET` permission** — camera and face data physically cannot leave the device.
- All processing is **on-device** (ML Kit / MediaPipe); camera frames are never stored or transmitted.
- Settings are stored **encrypted** (`EncryptedSharedPreferences`, AES-256, Android Keystore) and
  excluded from backups.
- A consent screen precedes any camera use.

## Tech stack

- **Kotlin**, **Jetpack Compose** (single-Activity + Compose Navigation), Material 3.
- iOS-inspired UI — "liquid glass" surfaces (real backdrop blur on Android 12+ via `haze`), floating
  tab bar, large titles, Inter font, systemBlue accent, light/dark themes.
- **CameraX** + **ML Kit Face Detection**; **MediaPipe Tasks Vision** (accuracy prototype).
- Foreground service, WorkManager, `EncryptedSharedPreferences`.
- Unit tests (pure logic isolated from Android) + instrumented Compose tests.
- `compileSdk` 37, `targetSdk` 36, `minSdk` 26.

## Build & run

```bash
# Point local.properties at your Android SDK (sdk.dir=...), then:
./gradlew :app:assembleDebug          # build debug APK
./gradlew :app:testDebugUnitTest      # run unit tests
./gradlew :app:installDebug           # install on a connected device
```

Requires Android Studio (AGP 9.x), JDK 11+, and a device/emulator with a working front camera.

## Screenshots

_To be added._

## Documentation

The complete engineering journal — architecture, dated decisions, audits, release notes, the
accuracy-prototype write-up, the backlog of future ideas, and the market/monetization research — lives
in **[`TECHNICAL_TASK.md`](TECHNICAL_TASK.md)**.

## License

Licensed under the **Apache License 2.0** — see [`LICENSE`](LICENSE).
Copyright 2026 Evgeniy Dammer.
