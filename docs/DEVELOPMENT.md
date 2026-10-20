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

GitHub Actions runs unit tests before assembling a debug APK. Check the actual workflow run for the result.

## Current implementation status
- Native Android Kotlin + Jetpack Compose + Material 3 shell.
- Arabic-first RTL layout with blue/cyan styling.
- Home, timetable, homework, preparations, and announcements navigation.
- Timetable UI can select PDF/image files and accept typed schedule text; the file is not uploaded.
- Local preview forms for homework, preparations, and administrative reports; these are persisted on-device with SharedPreferences.
- Administrative homework editing and deletion within the local preview.
- Selected timetable document URI is retained for local reopening where Android permits persistent URI access.
- Typed domain models, input-validation helpers, repository contract, role policy helpers, and unit tests.
- No login, backend/API, server database, live school data, messaging, push notifications, or cross-device synchronization. Local SharedPreferences persistence is present, but is not a substitute for a real school database.
- No Gradle wrapper has been committed yet.

## Role-policy intent
- Teachers publish homework and preparations for assigned subjects only.
- Class monitor can manage timetable entries and report homework to the principal, but does not directly delete homework.
- Principal and assistant principals manage timetable entries and moderate homework.
- Role selection in the UI is a preview control, not authentication or a security boundary.

## Security rules
- Do not commit credentials, tokens, private keys, or real student information.
- Treat client-supplied roles, subjects, school IDs, and user IDs as untrusted input.
- Enforce identity, role, subject assignment, school membership, class access, and conversation participant access on the server.
- Student-to-student messaging is excluded from version 1.
- Do not treat the project as production-ready until CI passes and backend authentication/authorization and file upload security are implemented and reviewed.
