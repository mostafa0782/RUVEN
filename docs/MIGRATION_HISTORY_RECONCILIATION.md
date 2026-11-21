# Supabase migration history reconciliation

**Audit date:** 2026-11-21  
**Project:** `ijvfjvfmpvydbclgzyac`

## Result

- The connected Supabase project reports **44 applied migration versions**.
- The repository now contains **44 SQL migration files**.
- A direct comparison of the 14-digit local filename prefixes with the remote version list returned **44 matches, 0 missing versions, 0 extra versions, and 0 duplicate local versions**.
- Older migration files were renamed to the actual remote version numbers. Two semantically equivalent historical files were renamed to the remote migration names.
- Two historical remote entries that had no local file were restored:
  - `lock_down_owner_profile_trigger_function`: repeats the trigger-only function EXECUTE revocation already included in the preceding owner-profile migration.
  - `ruven_include_counselor_in_school_admin`: retained as a no-op history entry because the following historical admin-role migration contains the corresponding helper definition; the later least-privilege migration removes counselor admin access from the final schema.
- The four latest security migrations were renamed to their actual remote version numbers after the migration tool recorded server-generated versions that differed from the supplied filename prefixes.

## Important boundary

This is **migration version alignment only**. It is not proof that the SQL files replay cleanly from an empty database.

- The repository does not yet contain `supabase/config.toml`.
- `supabase init` has not been run from a local development environment.
- `supabase db reset` has not been run against a clean local Supabase stack.
- `supabase db push` has not been run against the hosted project.
- No rows in Supabase's internal migration-history table were edited directly.

Do not run a production `db push` until local initialization, clean replay, `supabase migration list`, and a linked-project `supabase db push --dry-run` all complete without unexpected differences. Follow the exact sequence in `supabase/README.md`.

## Public repository privacy finding

An old owner-bootstrap migration contained a personal account identifier and automatically granted owner privileges based on a sign-up email. The live database no longer does this: the current `public.handle_new_auth_user()` always creates a student profile, and owner provisioning is separate.

The current historical migration file was sanitized so a fresh replay no longer embeds that personal identifier. Earlier public Git commits still contain the old value. Removing it from all Git history would require coordinated history rewriting and a force push; that operation is intentionally not performed without explicit approval. Do not mistake editing the current file for purging old commits.

## Initial owner setup

For a brand-new project, create and verify the first Auth account, then promote only its exact Auth UUID through the trusted SQL Editor bootstrap documented in `supabase/README.md`. The existing live project already has its owner profile; do not rerun the bootstrap statement there.

## Future migration procedure

1. Create each migration with `supabase migration new <descriptive_name>`.
2. Apply it to a local Supabase stack and run the relevant tests.
3. Confirm the new filename version is the version recorded by the remote project after deployment.
4. Keep the local and remote migration lists aligned.
5. Never edit Supabase's internal migration-history table directly.
