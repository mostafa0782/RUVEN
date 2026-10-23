# RUVEN — Supabase setup for the Android preview

## Current implementation
- Android uses the Supabase Auth password grant to sign in with email/password.
- After sign-in, it loads the caller's own row from `public.profiles`.
- The home screen fetches published school items from `public.school_items`; RLS remains the authority for which rows are visible.
- A missing profile blocks entry. This is intentional: the app must not let a user choose their own role or class.
- The app stores the rotating refresh token in app-private SharedPreferences and refreshes the session on startup. This token is not encrypted by the app yet; encrypted token storage should be completed before a production rollout.
- Published school items are loaded from Supabase. Several compose forms and tabs still use local preview data and are not yet writing to Supabase. Do not use this build as the school's production app.

## Provision the first trusted school administrator
Do this only with the school's authorization.

1. In Supabase Dashboard, open **Authentication → Users** and create the administrator account with the intended email and password. Do not use a shared password.
2. Copy the new user's UUID from the Users screen.
3. Open **SQL Editor** and run the following after replacing the two placeholders. Use the actual UUID copied from Supabase; do not paste a password or service-role key here.

```sql
insert into public.profiles (id, full_name, role, class_name, is_owner)
values (
  'REPLACE-WITH-AUTH-USER-UUID'::uuid,
  'REPLACE-WITH-ADMIN-DISPLAY-NAME',
  'principal',
  null,
  true
);
```

4. Sign in through RUVEN using that account's email and password.
5. Do not give students or teachers the administrator credentials. Provision each person their own account and profile with the correct role and class.
6. To create another staff/student profile, create their Auth user first, then insert their profile row using that Auth UUID. Students should receive `role='student'` and the correct `class_name`; teachers should have `role='teacher'`. Assign trusted roles only through administration.

## Security notes
- The Android app contains only the Supabase project URL and publishable key. Never put a `service_role` or secret key in Android, GitHub, or any client app.
- Profile insert is intentionally disabled for ordinary authenticated clients, so users cannot assign themselves a role or class.
- Review the security advisor findings before production rollout.


## RUVEN notification layer (current implementation)

- Android creates a dedicated high-importance **تحديثات المدرسة** notification channel with vibration and a visible app badge.
- After the first successful login sync, the app records the current published item IDs silently so students are not spammed with old content.
- Use the refresh icon in the signed-in header to sync published content. Newly discovered homework, preparations, timetable changes, reports, and announcements generate rich expandable Android notifications, with the title, subject/section where available, and the post body.
- Notification IDs are stable per content item to avoid duplicate alerts for the same item. Android 13+ asks for the notification permission at runtime.
- **Important limitation:** this is sync-triggered notification delivery, not remote push. It can notify about new items when a user opens or refreshes RUVEN, but it cannot guarantee instant delivery while the app is closed. True closed-app delivery requires a Firebase Cloud Messaging project, app-specific Firebase configuration, device-token registration, and a trusted server/Edge Function to send pushes. No Firebase credentials or service-role secrets should be placed in the Android app or committed to GitHub.
