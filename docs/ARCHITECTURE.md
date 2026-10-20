# RUVEN Architecture — Initial Foundation

## Current client

The Android client is a native Kotlin application built with Jetpack Compose and Material 3. The current UI is an initial shell: it has navigation and explicit empty states, but no account system or live school data.

## Source boundaries

- `iq.ruven.app`: Android entry point and initial Compose UI.
- `iq.ruven.app.data.model`: typed models for lessons, homework, and school announcements.
- `iq.ruven.app.data.model`: typed lesson, homework, and announcement records plus validation rules for API data.
- `iq.ruven.app.data.repository`: `SchoolContentRepository`, the suspendable contract for an authenticated data source, and `ValidatedSchoolContentRepository`, which rejects malformed records before presentation.

The repository contract is deliberately not backed by hard-coded demo content. UI features should represent loading, empty, and error states distinctly once a data source is connected.

## Planned feature boundaries

As the app grows, move UI state and data access into feature modules/packages:
- feature/auth
- feature/home
- feature/timetable
- feature/homework
- feature/announcements
- feature/messages
- feature/profile
- feature/settings
- core/designsystem
- core/network
- core/database

Introduce ViewModels, Room, DataStore, and a network client when a concrete feature needs them. Avoid unused infrastructure before requirements are known.

## Data and authorization

The current UI has no backend and contains no real student data. Every protected operation must be authorized server-side and scoped to the authenticated user's school, role, class, and relevant conversation. The client repository API does not accept arbitrary student or school IDs for content queries; the future data source must derive identity from the authenticated session. Any server-side client-supplied identifier is never proof of authorization.

## Messaging policy

Student-to-student messaging is out of scope for version 1. Messaging, when implemented, must be limited to explicitly authorized student-to-teacher or student-to-administration conversations. The server must enforce participant permissions on every read and write.

## Multi-school readiness

School-scoped data must be enforced by the backend. Never trust a school identifier supplied by a client without checking membership and permissions on the server.
