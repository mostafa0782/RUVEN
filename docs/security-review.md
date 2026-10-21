# RUVEN security review

## Applied changes
- Moved RLS helper functions (`current_school_class`, `current_school_role`, `is_school_admin`, `is_school_owner`, and `teacher_has_assignment`) into the non-exposed `private` schema.
- Updated table and storage RLS policies to call the private helpers.
- Revoked authenticated access to the old public helper RPCs.
- Added indexes covering foreign-key columns flagged by the Supabase advisor.
- Created a private `ruven-school-files` bucket with a 20 MiB size cap, MIME-type allowlist, and role-/publication-aware storage policies.
- Moved privileged profile-role changes to the `owner-set-profile-role` Edge Function. It validates the caller's JWT, verifies the caller's owner flag server-side, rejects owner promotion and protects the existing owner account, then updates the target profile with the service-role key kept server-side.
- Revoked `EXECUTE` for `public.owner_set_profile_role` from `PUBLIC`, `anon`, and `authenticated`, removing the direct authenticated RPC path.

## Remaining administrator action
The latest Supabase Security Advisor reports **Leaked Password Protection Disabled** as the remaining security warning. This is an Auth configuration setting, not a Postgres table or migration setting, and no connected tool exposes the required Auth configuration update. An authorized project administrator must enable it in the Supabase Dashboard under **Authentication → Settings (or Security/Password policy) → Leaked password protection**, then re-run the Security Advisor. Supabase may require an appropriate plan/configuration for this feature.

## Verification notes
- Re-run the Security Advisor after enabling leaked-password protection.
- The direct `owner_set_profile_role` RPC warning cleared after revoking client execution. Do not restore client grants or expose any service-role key to the app.
- Multiple permissive RLS policies are still flagged for performance. They have different authorization purposes; consolidate only after regression tests prove all role workflows remain valid.
- Indexes reported as unused may simply be new or not exercised by the current small dataset; do not delete them based solely on a fresh-project advisor snapshot.
