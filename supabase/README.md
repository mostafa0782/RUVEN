# RUVEN Supabase database

This folder defines the first shared PostgreSQL database for RUVEN. It uses Supabase PostgreSQL + Supabase Auth and Row Level Security (RLS), so data can be shared between school accounts instead of living only in each device's SharedPreferences.

## Included schema

- `profiles`: school identity and role attached to an authenticated Supabase user.
- `teacher_subjects`: explicit teacher-to-subject/class assignments.
- `school_items`: homework, preparations, timetable entries, announcements and monitor reports.
- `report_updates`: follow-up notes for administrative review.
- RLS policies restrict content access and writing by role and class.
- Teachers can publish homework/preparations only for assignments recorded in `teacher_subjects`.
- The app must never ship the Supabase service-role key. Use only the project's public/anon key on-device; RLS is the security boundary.

## Apply to a Supabase project

1. Create a Supabase project at https://supabase.com/.
2. Install the Supabase CLI in a development environment.
3. Run `supabase link --project-ref YOUR_PROJECT_REF`.
4. Run `supabase db push` from the repository root.
5. In Supabase Authentication, configure allowed sign-in methods and create/approve school accounts.
6. Assign roles and teacher subjects through a trusted administrator workflow. Do not expose role editing to students.
7. Add the project's URL and public anon key to Android local configuration (never commit real secrets).

## Current limitation

Committing these migrations creates the database definition in the repository; it does **not** create a hosted Supabase project or deploy it automatically. The app UI still uses local SharedPreferences until the Android data layer is wired to Supabase and project credentials are configured. See `docs/DATABASE.md` for the rollout plan.
