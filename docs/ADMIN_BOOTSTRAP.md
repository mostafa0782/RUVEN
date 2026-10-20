# RUVEN — First account and school setup

This guide is for the school owner/authorized administrator. Never put a Supabase `service_role` key in Android, GitHub, screenshots, or chat.

## 1. Create the first Auth user

1. Open the Supabase project `ruven-school-app`.
2. Go to **Authentication → Users → Add user**.
3. Create the dedicated **owner** account using the owner's controlled email and a strong password. This is not the principal role; `owner` is a separate highest-level role.
4. Confirm the user is listed. Do not send the password to the developer or put it in this repository.

## 2. Link that account to the school profile

1. Open **SQL Editor → New query**.
2. Paste the SQL below, replacing `OWNER_EMAIL_HERE` with the exact email used above and `OWNER_FULL_NAME_HERE` with the principal's name.
3. Run it. It grants owner status only to the account matched by that email.

```sql
insert into public.profiles (id, full_name, role, class_name, is_owner)
select id, 'OWNER_FULL_NAME_HERE', 'owner'::public.school_role, null, true
from auth.users
where lower(email) = lower('OWNER_EMAIL_HERE')
on conflict (id) do update
set full_name = excluded.full_name,
    role = 'owner'::public.school_role,
    class_name = null,
    is_owner = true;
```

4. Verify that exactly one intended row was changed. If SQL reports **0 rows**, the email does not match a user in Authentication → Users; correct the email or create the user first.

```sql
select p.id, p.full_name, p.role, p.class_name, p.is_owner
from public.profiles p
join auth.users u on u.id = p.id
where lower(u.email) = lower('OWNER_EMAIL_HERE');
```

## 3. Provision staff and students

Create each user in **Authentication → Users** first, then add a matching `public.profiles` row using the same Auth user UUID. Use only these role values:

- `student`
- `class_monitor`
- `teacher`
- `assistant_principal`
- `principal`
- `owner` (highest level; full school administration)

Use the exact school class/section naming convention agreed by the school (for example, grade + section A–F). Do not allow users to choose their own role or class at sign-up.

Example for a single staff member after their Auth user exists:

```sql
insert into public.profiles (id, full_name, role, class_name, is_owner)
select id, 'STAFF_FULL_NAME_HERE', 'teacher'::public.school_role, 'GRADE_3_C', false
from auth.users
where lower(email) = lower('STAFF_EMAIL_HERE');
```

For teachers, also add their assigned subject/class rows to `public.teacher_subjects`. Use the exact names that will be used by the app, otherwise the assignment check will reject posts.

## 4. Open a section

Only the owner should create/open sections. Example:

```sql
insert into public.class_sections (grade_name, section_code, is_open)
values ('GRADE_3', 'C', true)
on conflict (grade_name, section_code)
do update set is_open = excluded.is_open;
```

Replace the example grade with the school-approved canonical value. Repeat for the sections the school wants to expose.

## 5. Configure Android build secrets

The Android app reads Gradle properties named `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY`. Configure them in a local untracked `~/.gradle/gradle.properties` file or as CI secrets. The URL is the project's HTTPS URL and the key is the publishable/anon client key only. Never use the `service_role` secret in Android.

## Important release gate

Creating the owner/profile is only account provisioning. Before distributing the APK, verify the app builds, a student cannot publish content, a teacher cannot post outside assigned subjects/classes, and the owner has full school-data administration, while principal/assistant principal retain their school-level moderation permissions. The current UI still contains local-only controls in some screens; do not treat them as live school features until they have been wired to the backend and tested.
