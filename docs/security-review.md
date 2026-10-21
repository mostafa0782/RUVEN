# RUVEN security review

## Applied changes
- Moved RLS helper functions (`current_school_class`, `current_school_role`, `is_school_admin`, `is_school_owner`, and `teacher_has_assignment`) into the non-exposed `private` schema.
- Updated table and storage RLS policies to call the private helpers.
- Revoked authenticated access to the old public helper RPCs.
- Added indexes covering foreign-key columns flagged by the Supabase advisor.
- Created a private `ruven-school-files` bucket with a 20 MiB size cap, MIME-type allowlist, and role-/publication-aware storage policies.
- Kept `owner_set_profile_role` as a guarded SECURITY DEFINER RPC because the mobile app needs to change protected profile role fields that ordinary clients cannot update directly. The function checks the trusted owner flag, prevents creating another owner, and protects the owner account.

## Remaining administrator action
Supabase currently reports **Leaked Password Protection Disabled**. This is an Auth configuration setting, not a Postgres table or migration setting, and no connected tool exposes the required Auth configuration update. An authorized project administrator must enable it in the Supabase Dashboard under **Authentication → Settings (or Security/Password policy) → Leaked password protection**, then re-run the Security Advisor. Supabase may require an appropriate plan/configuration for this feature.

## Verification notes
- Re-run the Security Advisor after enabling leaked-password protection.
- The remaining SECURITY DEFINER warning for `public.owner_set_profile_role` is intentional and guarded; do not remove its owner checks or expose any service-role key to the app.
- Multiple permissive RLS policies are still flagged for performance. They have different authorization purposes; consolidate only after regression tests prove all role workflows remain valid.
- Indexes reported as unused may simply be new or not exercised by the current small dataset; do not delete them based solely on a fresh-project advisor snapshot.
