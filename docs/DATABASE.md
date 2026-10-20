# RUVEN database integration plan

## Database choice

RUVEN uses Supabase (managed PostgreSQL + Auth + Row Level Security) for the shared school database. PostgreSQL gives structured relationships and constraints; Supabase provides hosted authentication and database APIs without requiring a self-managed server.

## Data model

- `profiles`: one profile per `auth.users` account; role is student, class monitor, teacher, assistant principal or principal.
- `teacher_subjects`: authorized subject/class assignments.
- `school_items`: one normalized table for homework, preparations, timetable, announcements and reports, distinguished by `item_type`.
- `report_updates`: administrative follow-up on reports.

## Security requirements

- RLS is enabled on every public table.
- Role claims must not be accepted from Android input; roles are assigned through a trusted administrative path.
- Teachers can create homework/preparations only for their explicit subject and class assignment.
- Students see published content for their class, not unpublished drafts for other classes.
- Only principal/assistant-principal roles can administer school content.
- The Supabase service-role secret must never be included in the APK, source code or client configuration.

## Implementation sequence

1. Apply `supabase/migrations/202610200001_initial_schema.sql` to a real Supabase project.
2. Configure school account provisioning and role assignment securely.
3. Add the Supabase Kotlin client and read URL/public anon key from untracked local configuration.
4. Replace the local SharedPreferences repository with a repository backed by Supabase; preserve local drafts only as offline cache.
5. Upload timetable attachments to a private Storage bucket and use RLS-based storage policies.
6. Add integration tests for student, monitor, teacher, assistant and principal access.
7. Validate policies in a test project before deploying to the school's real accounts.

## Status

The migration and docs are committed as source. No hosted project has been created or connected by this repository change, and the current Compose screens still use local storage. Do not represent this as a live cloud database until migration, credentials, authentication and Android integration are complete.
