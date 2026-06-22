# Android Student App (Phase 9) — Project Skeleton

This directory contains a minimal Android project skeleton intended to integrate the Student Android app with the frozen backend.

Notes:
- This is a project skeleton (Kotlin, MVVM, Retrofit) created for Phase 9. It is not a full-featured app and is intended to be completed and built locally with Android Studio / Gradle and an Android SDK installed.
- I cannot run Android builds or instrumentation tests in this environment. See "Local Build" below for commands to run locally.

Local Build (run on developer machine with Android SDK & JDK 11+):

```bash
cd android
./gradlew :app:assembleDebug
./gradlew :app:test
```

Open the project in Android Studio to run emulators and instrumentation tests.
