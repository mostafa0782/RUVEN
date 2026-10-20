# RUVEN

RUVEN is an Android-first school services app for Iraqi schools, beginning with Al-Mutafawiqeen Secondary School in Nasiriyah, Dhi Qar, Iraq.

## Current status

This repository is in the foundation stage. The current Android shell includes:
- Native Android application using Kotlin and Jetpack Compose.
- Arabic-first interface and RTL support enabled at application level.
- A custom blue/cyan visual direction.
- Initial home, timetable, homework, and announcements navigation.
- Explicit empty states so unconnected data is not presented as real school information.

Not implemented yet: authentication, backend/API, database, live school data, messaging, push notifications, and role-based authorization.

## Build prerequisites

- JDK 17
- Android SDK with API 35 installed
- Android Studio compatible with Android Gradle Plugin 8.9.1

A Gradle wrapper has not yet been added. Open the repository in Android Studio, sync Gradle, and run the app configuration. With a compatible installed Gradle, run `gradle :app:assembleDebug`.

## Development principles

- Keep development on `feature/ruven-foundation` until reviewed.
- Do not commit secrets or real student data.
- Do not describe placeholder screens as connected functionality.
- Enforce authorization in the backend when it is implemented.
