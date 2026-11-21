# RUVEN Supabase database

RUVEN uses Supabase PostgreSQL, Auth, Storage, Edge Functions, and row-level security (RLS). The Android client contains only the publishable key. The service-role key is server-only.

## Live project state

- The connected Supabase project already has 44 recorded migrations.
- The repository now contains 44 migration files whose 14-digit filename versions match the live migration version list (44/44, compared directly through the Supabase and GitHub APIs).
- This is a **single-school** database. Do not onboard another school until tenant-aware membership, RLS, and Storage boundaries are implemented.
- The live database has RLS enabled on all 15 public application tables.
- No `supabase/config.toml` is currently committed. Local CLI replay has not yet been run.

## Safe local setup and migration verification

Do not run `supabase db push` against the linked production project until the following checks have passed:

1. Install the Supabase CLI in a development environment.
2. Run `supabase init` in the repository root to create the CLI configuration. Keep the local project ID separate from the hosted project reference.
3. Run `supabase link --project-ref ijvfjvfmpvydbclgzyac`.
4. Run `supabase migration list` and confirm every local migration version is paired with the corresponding remote version.
5. Run `supabase db reset` locally and verify that all migrations replay successfully from an empty local database.
6. Run `supabase db push --dry-run` against the linked project. Stop if it reports any unexpected pending migration.
7. Do not run a production push unless the dry run is clean and a current database backup exists. The live project already has the current migrations applied; a push should not be needed for this branch.

For future schema changes, create the migration with `supabase migration new <descriptive_name>`, test it locally, then apply it through the approved deployment process. Avoid creating a migration-history record separately from its matching local SQL file.

See `docs/MIGRATION_HISTORY_RECONCILIATION.md` for the filename/history audit and remaining verification boundary.

## Initial owner bootstrap

New Auth users always receive the least-privileged `student` role. Owner privileges must never be assigned from a sign-up email address or user-supplied metadata.

For a brand-new project only:

1. Create the first trusted Auth user through the Supabase dashboard.
2. Verify the account with the project administrator and copy its Auth user UUID.
3. In the SQL Editor, promote only that exact UUID:

```sql
UPDATE public.profiles
SET role = 'owner'::public.school_role,
    is_owner = true,
    class_name = NULL
WHERE id = '<AUTH_USER_UUID>';
```

4. Confirm exactly one row was updated and verify the profile. Only then use the owner-only Edge Functions for normal account provisioning.

The connected live project already has its owner profile; do not rerun this bootstrap SQL there.

## Security invariants

- Never ship the service-role key in Android, source code, build configuration, or client-visible files.
- Do not let clients choose their own role, owner flag, reviewer, uploader, or actor identity.
- Keep the Storage bucket private and require approved catalogue metadata before students can download textbooks or 3D resources.
- Do not treat UI visibility as authorization; database RLS and server-side checks are authoritative.
- Do not treat the current schema as multi-school safe.
