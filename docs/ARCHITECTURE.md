# RUVEN Architecture — Foundation and UI Prototype

## Current client

The Android client is a native Kotlin application built with Jetpack Compose and Material 3. It has Arabic-first RTL screens for home, timetable, homework, preparations, and announcements. Content creation is currently a local UI preview, not a connected service. Principals and assistant principals can create/delete local announcement entries; this does not publish to other devices. Homework, timetable descriptions and selected document URIs, preparations, and reports are persisted on-device through SharedPreferences.

## Source boundaries

- `iq.ruven.app`: Android entry point and Compose UI.
- `iq.ruven.app.data.model`: typed lesson, homework, announcement, preparation, and school-role models.
- `SchoolModelValidation`: input-quality validation for untrusted school records.
- `SchoolPermissions`: pure role-policy helpers for unit tests; these helpers are not authorization.
- `iq.ruven.app.data.repository`: `SchoolContentRepository` contract and `ValidatedSchoolContentRepository` decorator.

## Intended school-content permissions

- Teachers create homework and preparations only for subjects assigned to their authenticated account.
- Class monitors can submit homework and timetable content and report content that requires administrative review; they cannot delete or edit homework directly.
- The principal and assistant principals can manage timetable content and moderate homework.
- The timetable UI supports text entry and selecting PDF/image files.
- The current role dropdown is a prototype-only way to preview screens. It does not authenticate users. Selected PDF/image URIs can be reopened locally where Android grants persistent URI access; files are not uploaded.

The future backend must check the authenticated principal, assigned subjects, school/class membership, and action-specific permission for every read/write. Never accept a client-provided role or subject name as proof of permission.

## Data and authorization

The current UI has no backend and contains no real student data. Every protected operation must be authorized server-side and scoped to the authenticated user's school and class. The client repository API does not accept arbitrary student or school IDs for content queries; the future data source must derive identity from the authenticated session.

## Planned feature boundaries

As the app grows, move UI state and data access into feature modules/packages:
- feature/auth
- feature/home
- feature/timetable
- feature/homework
- feature/preparations
- feature/announcements
- feature/messages
- feature/profile
- feature/settings
- core/designsystem
- core/network
- core/database

Replace the prototype SharedPreferences store with Room for structured offline content, add ViewModels, and introduce a network client when the authenticated backend is available.

## Messaging policy

Student-to-student messaging is out of scope for version 1. Messaging, when implemented, must be limited to explicitly authorized student-to-teacher or student-to-administration conversations. The server must enforce participant permissions on every read and write.

## Multi-school readiness

School-scoped data must be enforced by the backend. Never trust a school identifier supplied by a client without checking membership and permissions on the server.
