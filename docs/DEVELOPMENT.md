# Development Guide

## Requirements
- JDK 17
- Android SDK API 35
- Android Studio with support for Android Gradle Plugin 8.9.1
- A compatible Gradle installation (the wrapper is not yet committed)

## Open and build
1. Open the repository in Android Studio.
2. Sync the Gradle project.
3. Resolve any SDK component prompts.
4. Run the app configuration on an emulator or Android device.

From a terminal with a compatible Gradle installation, run: gradle :app:assembleDebug

## Current limitations
- No login or user accounts.
- No backend, API, or database.
- No real timetable, homework, announcements, or messaging data.
- No push notifications.
- No automated tests have been executed in this remote GitHub-only workflow.
- Add the Gradle wrapper and run a real build before treating the foundation as verified.
