# RUVEN

RUVEN is an Android-first school services app for Al-Mutafawiqeen Secondary School in Nasiriyah, Dhi Qar, Iraq.

## Current implementation

- Native Android app built with Kotlin, Jetpack Compose, and Material 3.
- Arabic-first interface with RTL support.
- Animated launch screen with RUVEN branding and a temporary, illustrative school emblem.
- Supabase email/password authentication, refresh-token session restoration, and profile loading.
- Server-backed loading of published school items, with class-aware filtering expected to be enforced by database policies.
- Owner-only management flows call server-side functions/RPCs for account creation and profile-role changes.
- Local device persistence for selected school content and UI workflows; not every screen is server-synchronized.
- Local notification processing for newly synchronized published items.
- In-app update checker reads the latest GitHub Release, requires `RUVEN.apk` plus `RUVEN.apk.sha256`, and verifies the downloaded APK's SHA-256 before opening Android's installer.

## Important limitations before public deployment

- No official GitHub Release is published yet. Workflow artifacts are temporary test/build outputs, not public app releases.
- The launch emblem is an approximation, not the school's original logo; the Python mark is a simple illustration, not the official Python logo.
- Test the app on a real Android device, including login, session restoration, role permissions, network errors, notifications, and update installation.
- Confirm Supabase tables, Row Level Security (RLS) policies, database RPCs, and the `owner-create-account` Edge Function are deployed and correctly restricted. Client-side UI checks are not an authorization boundary.
- Do not publish until stable APK signing is confirmed to match the installed app. Replacing the signing key can prevent in-place updates.
- Local-only content is not automatically synchronized between users.

## GitHub Actions secrets

The Android build and tagged-release workflows require these repository Actions secrets:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`
The Supabase project URL and publishable client key are configured as public client settings in `app/build.gradle.kts`; they can be overridden with Gradle properties when building for a different project. A publishable key is intended for client apps, but it does not replace Row Level Security (RLS). Never place a Supabase service-role key in the Android app or repository.

## Build

- JDK 17
- Android SDK platform 35 and build-tools 35.0.0
- Gradle 8.11.1 (the repository does not currently include a Gradle wrapper)

The CI workflow runs unit tests, builds a signed release APK, and uploads it as the `RUVEN-release-apk` artifact.

## Versioning and publishing

The build workflow uses `0.1.<GitHub Actions run number>` as the version name and the run number as Android `versionCode`. For the current `0.1.x` series, publish a matching numeric tag such as `v0.1.132` only after reviewing and testing that source. The tag workflow creates a GitHub Release with `RUVEN.apk` and its `RUVEN.apk.sha256` checksum. The updater rejects missing checksums and downloads from unexpected hosts.

## Security and release checklist

1. Confirm all required Actions secrets are configured.
2. Verify Supabase RLS and privileged server-side functions independently of the Android UI.
3. Run CI tests and inspect the generated artifact.
4. Test installation/update signature compatibility and app behavior on a real device.
5. Publish a tagged release only after the previous checks pass.
