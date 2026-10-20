# RUVEN

RUVEN is an Android-first school services app for Iraqi schools, beginning with Al-Mutafawiqeen Secondary School in Nasiriyah, Dhi Qar, Iraq.

## Current status

The Android client foundation includes:
- Native Android application using Kotlin and Jetpack Compose.
- Arabic-first interface, RTL layout, and a blue/cyan visual system.
- Home dashboard and navigation for timetable, homework, preparations, and announcements.
- Timetable form with a system file picker for PDF/image selection plus a text entry option.
- Local preview flows for homework, teacher preparations, administrative reports, and school announcements, persisted on-device using SharedPreferences.
- Principal/assistant-principal announcement creation and deletion in the local preview.
- Administrative homework edit/delete controls in the local preview.
- Persisted URI access for a selected timetable document, with a local open-file action.
- Typed school-content models, data validation, and role-policy unit tests.

**Important limitation:** this is still a UI prototype. Preview content is persisted on this device with SharedPreferences; it is not synced between users or backed up to a school server. Announcements are also local to this device and are not broadcast to other users. A selected PDF/image can be reopened locally when Android preserves access, but it is not uploaded. There is no authentication, backend/API, server database, live school data, messaging, push notifications, or production role enforcement yet.

## Intended role policy

- Teachers can publish homework and preparations only for subjects assigned to their authenticated account.
- The class monitor can publish homework and manage timetable submissions, and can report problematic homework to the principal; the monitor does not own the delete permission.
- The principal and assistant principals can manage the timetable and moderate homework.
- Timetable entry supports text or selection of a PDF/image file.
- The role selector currently exists only to preview UI states. It is not authentication and must not be used as a security boundary.

All permissions must be enforced by a backend using the verified session, teacher-subject assignments, school, and class membership.

## Build prerequisites

- JDK 17
- Android SDK with API 35 installed
- Android Studio compatible with Android Gradle Plugin 8.9.1

A Gradle wrapper has not yet been added. Open the repository in Android Studio, sync Gradle, and run the app configuration. With a compatible installed Gradle, run `gradle :app:assembleDebug`.

## Development principles

- Keep feature work on dedicated `feature/*` branches until reviewed; do not merge into `main` without a deliberate review.
- Do not commit secrets or real student data.
- Do not describe local preview functionality as connected functionality.
- Enforce authorization in the backend when it is implemented.
