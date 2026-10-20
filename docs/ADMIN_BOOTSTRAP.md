# RUVEN — First account and school setup

This guide is for the school owner/authorized administrator. Never put a Supabase `service_role` key in Android, GitHub, screenshots, or chat.

## 1. Owner account (automatic email allowlist)

The database is configured so that **`mostafaminecraft472@gmail.com`** receives the `owner` role automatically when that Auth user is created. If the account already existed when the migration was applied, its profile was also promoted automatically.

1. Open the Supabase project `ruven-school-app`.
2. Go to **Authentication → Users → Add user** (or use the app's approved sign-up flow if enabled).
3. Create/sign in with the exact email `mostafaminecraft472@gmail.com` and choose a strong password that only you know.
4. After the Auth account is created, its `public.profiles` row is created automatically with display name `mostafapython`, role `owner`, and `is_owner = true`. No manual SQL step is needed.
5. Sign in again / refresh the session if you were already logged in before the role was assigned.

**Security:** the owner grant is based on the exact verified Auth email, not a username or user-editable metadata. Do not let other people access this email inbox or account. All other newly created users receive the `student` role by default; never accept a role selected by the client.

## 2. Provision staff and students

Create users through the approved Auth workflow. The database trigger creates their profiles with the `student` role by default. An authorized owner should change roles only through a protected administrative workflow.

Use only these role values:

- `student`
- `class_monitor`
- `teacher`
- `assistant_principal`
- `principal`
- `owner` (highest level; full school administration)

Use the exact school class/section naming convention agreed by the school (for example, grade + section A–F). Do not allow users to choose their own role or class at sign-up.

For teachers, also add their assigned subject/class rows to `public.teacher_subjects`. Use the exact names that will be used by the app, otherwise the assignment check will reject posts.

## 3. Open a section

Only the owner should create/open sections. Example:

```sql
insert into public.class_sections (grade_name, section_code, is_open)
values ('GRADE_3', 'C', true)
on conflict (grade_name, section_code)
do update set is_open = excluded.is_open;
```

Replace the example grade with the school-approved canonical value. Repeat for the sections the school wants to expose.

## 4. Configure Android build secrets

The Android app reads Gradle properties named `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`. Configure them in a local untracked `~/.gradle/gradle.properties` file or as CI secrets. The URL is the project's HTTPS URL and the key is the publishable/anon client key only. Never use the `service_role` secret in Android.

## Important release gate

Creating the owner/profile is only account provisioning. Before distributing the APK, verify the app builds, a student cannot publish content, a teacher cannot post outside assigned subjects/classes, and the owner has full school-data administration, while principal/assistant principal retain their school-level moderation permissions. The current UI still contains local-only controls in some screens; do not treat them as live school features until they have been wired to the backend and tested.
