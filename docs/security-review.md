# RUVEN security review

**Audit date:** 2026-11-21  
**Scope:** Live Supabase database and Storage, Auth/Edge Functions, Android client, updater, and GitHub Actions release workflow.  
**Status:** Hardening changes committed to `feature/ruven-announcements`; final CI verification is pending for the latest commit.

## Verified database controls

- RLS is enabled on all 15 public application tables. No public application table was found with RLS disabled.
- `anon` has no table-level grants on the 15 public application tables. Authenticated access is governed by the reviewed RLS policies.
- Role/authorization helpers are in the non-exposed `private` schema, use `SECURITY DEFINER` with an empty `search_path`, and derive role/class from the authenticated profile rather than client-supplied claims.
- The Auth user trigger always creates a least-privileged `student` profile and ignores user-supplied role or owner metadata.
- **Fixed 2026-11-21:** class-monitor leave-request visibility now requires both the student's class and the monitor's own class to match. Class-monitor report creation and report updates are restricted to their own class and their own report records. Assigned decision recipients must be `class_monitor` accounts, and only a class monitor can read a decision through the assigned-monitor branch.
- **Fixed 2026-11-21:** `educational_counselor` was removed from `private.is_school_admin()` and from the Android admin-workspace helper. Broad school-wide administration is now limited to owner, principal, and assistant principal. Counselor-specific access must be designed as narrow policies rather than inheriting admin access.
- The private `ruven-school-files` bucket enforces role-, publication-, and approval-aware access. Restrictive INSERT policies prevent ordinary teacher/monitor upload grants from bypassing admin-only textbook and 3D catalog paths.
- Trusted-actor triggers derive uploader, reviewer, report creator, leave reviewer/attendance marker, and decision execution actor from `auth.uid()`.
- Overlapping permissive RLS policies were consolidated while preserving their previous predicates. RLS probes and rollback-based workflow tests passed.
- `anon` and `authenticated` have no `TRUNCATE`, `TRIGGER`, `REFERENCES`, or `MAINTAIN` privileges on public tables. The same defaults are revoked for tables created by the `postgres` migration role.
- **Completed 2026-11-21:** the same unnecessary table DDL/maintenance privileges were revoked from `service_role` on all current public tables and from `postgres`-created future tables. Live verification returned zero such privileges across the 15 public tables.
- The two active Edge Functions verify the caller's JWT and the caller's `profiles.is_owner` flag before account provisioning or role changes. The service-role key remains server-side.
- `service_role` can SELECT profiles and update only the provisioning columns `email`, `full_name`, `role`, `class_name`, and `is_owner`; it cannot update `id` or `created_at`.

## Android and release hardening

The following source changes are committed; the final Android CI run must pass before calling them build-verified:

- Refresh tokens are stored with AES-GCM encryption using a non-exportable Android Keystore key. Existing plaintext refresh-token preferences are migrated and removed.
- App backup is disabled and cleartext network traffic is explicitly disabled.
- FileProvider access is narrowed to the updater's dedicated cache directory rather than the entire cache.
- The updater accepts only HTTPS release assets from this repository, validates the final download host, enforces a 100 MiB download ceiling, verifies SHA-256, and deletes incomplete or invalid downloads.
- Sign-out now revokes the session once rather than issuing the same request twice.
- The tagged-release workflow rejects non-numeric versions and prevents a 0.1.x release whose Android `versionCode` would be less than or equal to the latest CI build number on that exact commit.

## Verification limitations

The affected workflow tables were empty when the cross-class policies were corrected, so no existing workflow records required migration. The updated policy definitions were re-read from the live catalog. Dedicated live negative tests using separate authenticated class-monitor accounts are still required before production launch; the current tool session did not complete a synthetic cross-role fixture.

## Outstanding blockers and weaknesses

1. **Multi-school isolation is not implemented.** No public application table has a tenant identifier or school membership boundary. Treat the database as single-school only. Do not onboard a second school into the same project until tenant-aware RLS, parent-child constraints, Storage paths, and cross-tenant negative tests are complete.
2. **Leaked-password protection is disabled.** Supabase Security Advisor still reports this Auth warning. It is an Auth dashboard configuration, not a SQL migration; an authorized project administrator must enable it and rerun the advisor.
3. **`supabase_admin` default ACL remains a platform-level caveat.** Its default ACL still grants client roles broad privileges on future objects it creates. The available migration role cannot alter this superuser-owned default. Continue creating app objects as `postgres`; platform-level remediation is needed before creating application tables as `supabase_admin`.
4. **Provisioning rate limits are not implemented in the Edge Functions.** The endpoints are owner-only, but a database-backed per-owner rate limit and abuse audit should be added before wider deployment.
5. **Privacy policy is still a draft.** School approval, official contact details, retention periods, deletion procedures, and guardian notices must be confirmed before public deployment.
6. **CI coverage and supply-chain hardening are incomplete.** Feature/PR workflows no longer receive signing secrets; a separate workflow signs only the exact successful `main` commit, and manual release publishing checks that the tag is an ancestor of `main`. Configure the `release-signing` environment with main-only deployment and required reviewers. Automated UI smoke tests, dependency vulnerability scanning, and action pinning to immutable commit SHAs remain to be added.
7. **Unused-index findings are informational.** The advisor reports 29 unused indexes while the database contains very little production data. Do not drop them without representative workload measurements.

## Verification boundary

A passing CI build does not prove correct behavior on a physical device or complete production security. Before release, test login/session migration, sign-out, role boundaries, storage upload/download, updater installation, and notification behavior on supported Android devices. Never put a Supabase service-role key in the APK or repository.
