# RUVEN Architecture — Initial Foundation

## Current client

The Android client is a native Kotlin application built with Jetpack Compose and Material 3. The current implementation is intentionally small while the project is being established.

## Planned boundaries

As features grow, separate UI state and data access by feature:
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

Introduce ViewModels, repositories, Room, DataStore, and a network client only when a concrete feature needs them. Avoid adding unused infrastructure before its requirements are known.

## Data and authorization

The current UI has no backend and contains no real student data. When the backend is designed, every protected operation must be authorized server-side and scoped to the user's school, role, and relevant class or conversation.

## Multi-school readiness

School-scoped data must be enforced by the backend. A school identifier supplied by a client is not proof of access.
