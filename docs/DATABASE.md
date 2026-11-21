# RUVEN database and authorization

## Platform

RUVEN uses Supabase Auth, managed PostgreSQL, PostgREST, Edge Functions, and private Storage. The Android application uses the Supabase publishable key only. Never include a service-role key in the APK, client configuration, or repository.

## Live application tables

The current public schema contains 15 application tables:

- Identity and settings: `profiles`, `app_user_settings`
- Class structure: `class_sections`, `teacher_subjects`, `student_roster`
- School content: `school_items`, `report_updates`, `daily_class_reports`
- Workflows: `leave_requests`, `school_decisions`, `school_seals`, `user_notifications`
- Learning: `student_study_records`, `textbook_catalog`, `study_3d_resources`

RLS is enabled on all 15 public application tables. The role and class helpers are stored in the non-exposed `private` schema. The broad school-admin helper includes only owner, principal, and assistant principal; educational counselors are not administrators. The Auth trigger creates new profiles with the least-privileged `student` role; trusted provisioning is performed separately.

## Privileged operations

The active Edge Functions are:

- `owner-create-account`: validates the caller's Supabase JWT and checks `profiles.is_owner` before using the Auth Admin API and configuring the new profile.
- `owner-set-profile-role`: validates the caller and owner flag, protects the owner account, and updates the target role/class.

The service-role key remains server-side. Database grants allow service-role profile SELECT and only the required profile update columns. Unnecessary `TRUNCATE`, `TRIGGER`, `REFERENCES`, and `MAINTAIN` privileges have been revoked from service_role, anon, and authenticated on existing public tables; postgres-created future tables inherit the restrictive defaults.

## Storage

The `ruven-school-files` bucket is private. Ordinary class attachments are controlled by authenticated role and published-content policies. Textbook and 3D catalogue upload paths have restrictive admin-only INSERT checks. Student reads require approved catalogue metadata.

## Important tenant limitation

The live schema does not yet contain `school_id`, `tenant_id`, or a school-membership relation. Role/class policies do not isolate two schools that happen to use the same class names. Operate as one school per Supabase project. Multi-school onboarding is blocked until tenant IDs, membership, composite parent-child constraints, tenant-aware RLS, tenant-scoped Storage paths, and cross-school negative tests are implemented.

## Remaining administrator action

Supabase Security Advisor reports leaked-password protection disabled. Enable it in the Supabase Auth dashboard, then rerun the Security Advisor. This setting is not controlled by a Postgres migration.

## Cross-class boundaries\n\nClass-monitor access to approved leave requests is scoped to the monitor's class. Monitor report creation/updates are scoped to their own class and report records. Decision assignment validates that the assigned account is a class monitor, and the assigned-monitor read path validates the caller's role. These policies were re-read from the live catalog after migration; a separate multi-account negative-test matrix remains required before launch.\n\n## Testing and rollout

- Run unit tests and release assembly in GitHub Actions.
- Run authenticated-role RLS probes for allowed and denied operations.
- Test Storage upload/download/delete paths for each role.
- Test account creation and role changes through the Edge Functions.
- Test session restoration, sign-out, and update installation on physical Android devices.
- Do not use real student data in test accounts.
