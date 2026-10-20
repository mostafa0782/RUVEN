# Development Guide

## Requirements
- JDK 17
- Android SDK platform 35 and build tools 35.0.0
- Android Studio compatible with Android Gradle Plugin 8.9.1
- Gradle 8.11.1 for command-line builds

## Open and build
1. Open the repository in Android Studio.
2. Sync the Gradle project.
3. Install Android SDK platform 35 and build tools 35.0.0 if prompted.
4. Run the app on an emulator or Android device.

From a terminal with Gradle 8.11.1 installed, run:

```sh
gradle :app:assembleDebug
```

The GitHub Actions workflow performs the same debug-APK assembly on pushes to `main` and `feature/ruven-foundation`, and on pull requests targeting `main`. Check the workflow run itself for the actual result; a configured workflow is not proof of a successful build.

## Current implementation status
- Native Android Kotlin + Jetpack Compose + Material 3 shell.
- Arabic-first UI with explicit RTL layout.
- Navigation shell for home, timetable, homework, and announcements.
- Typed domain models and a repository contract for school content.
- No login, backend/API, database, live school data, messaging, or push notifications.
- No Gradle wrapper has been committed yet.
- Do not treat the project as production-ready until the debug build is green, tests are added and run, and authentication/authorization and backend security have been implemented and reviewed.

## Security rules
- Do not commit credentials, tokens, private keys, or real student information.
- Treat client-side school and user identifiers as untrusted input.
- Enforce identity, role, school membership, class access, and conversation participant access on the server.
- Student-to-student messaging is excluded from version 1.
