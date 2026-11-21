# RUVEN Architecture

## Product and client

RUVEN is a native Android school-services application built with Kotlin, Jetpack Compose, and Material 3. It is Arabic-first with RTL support and an English localization path. The application includes authenticated school workflows, a student toolkit, a personal study space, PDF reading, a GLB study-model viewer, notifications, and appearance customization.

## Runtime boundaries

- **Android UI:** Compose screens and role-aware navigation. UI role checks improve usability only; they are not an authorization boundary.
- **Backend client:** `iq.ruven.app.data.remote.SupabaseBackend` sends HTTPS requests to Supabase Auth, PostgREST, Storage, and Edge Functions. The client contains only the publishable key.
- **Authentication:** Supabase email/password sessions. The refresh token is encrypted at rest with Android Keystore AES-GCM; access tokens remain in memory. The app disables Android backup.
- **Database:** PostgreSQL tables with RLS and database constraints. Authorization helpers live in the non-exposed `private` schema.
- **Privileged account management:** `owner-create-account` and `owner-set-profile-role` Edge Functions verify the caller's JWT and owner flag server-side. The service-role key is server-only and has narrowly scoped profile update grants.
- **Storage:** private `ruven-school-files` bucket. Textbooks and 3D resources require admin upload and approval metadata; ordinary class attachments follow role/publication-aware policies.
- **Local persistence:** selected study records, study-space state, appearance settings, and personal toolkit data are stored locally. Not every feature is cloud-synchronized.
- **Release updates:** the app reads the latest GitHub Release, requires an APK and SHA-256 asset, validates repository release URLs and download size, verifies the digest, then opens Android's package installer.

## Data and authorization model

The current database is a **single-school deployment**. RLS derives the caller's role and class from the authenticated profile. Class names are not tenant identifiers. No second school may be onboarded to the same Supabase project until a school membership model and tenant-scoped policies are deployed and tested.

The 15 public application tables cover profiles, class sections, teacher assignments, school content, reports, leave requests, decisions, seals, roster, notifications, study records, textbook catalogue, 3D resources, and user settings. RLS is enabled on all 15.

## Security invariants

1. Never trust a role, owner flag, or school ID supplied by the Android client.
2. Keep service-role and other privileged secrets on the server only.
3. Derive actor identities and review timestamps in database triggers for sensitive workflows.
4. Keep the Storage bucket private and require catalogue approval before student access to textbooks or 3D models.
5. Treat all client-side role checks as presentation logic only.
6. Do not enable multi-school onboarding until every school-owned table, parent reference, and Storage path is tenant-scoped and cross-tenant negative tests pass.

## Current limitations

- Multi-school tenant isolation is not yet implemented.
- Supabase Auth leaked-password protection remains disabled and requires dashboard configuration.
- The privacy policy remains a draft pending school approval and confirmed retention/guardian processes.
- Some student tools are local helpers rather than integrated timetable/homework workflows.
- CI currently covers unit tests, release assembly, and APK signature verification; UI smoke tests and dependency scanning remain future work.

See `docs/DATABASE.md`, `docs/RUVEN_TENANT_ISOLATION_AUDIT.md`, and `docs/FEATURES_90_IMPLEMENTATION_PLAN.md` for details.
