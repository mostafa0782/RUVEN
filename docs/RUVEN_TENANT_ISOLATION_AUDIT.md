# RUVEN Tenant-Isolation Audit

**Audit date:** 2026-11-21  
**Scope:** Live Supabase schema, RLS policies, Storage policies, and current migrations.  
**Status:** Single-school deployment assumptions; **not approved for multiple schools in one shared Supabase project**.

## Executive finding

The current database has no tenant identifier on any public application table. The live schema inspection found no `school_id`, `school_uuid`, or `tenant_id` column in:

- `profiles`, `class_sections`, `teacher_subjects`
- `school_items`, `report_updates`, `daily_class_reports`
- `leave_requests`, `school_decisions`, `school_seals`
- `student_roster`, `user_notifications`
- `student_study_records`, `textbook_catalog`
- `app_user_settings`

Current authorization helpers such as `private.is_school_admin()`, `private.is_school_owner()`, `private.current_school_role()`, and `private.current_school_class()` derive authorization from the caller's profile, but they do not establish a school boundary. Class-name equality is not a tenant boundary: two schools can both have a class named "الثالث متوسط".

Consequently, the current design must be treated as one school per Supabase project. Do not onboard a second school into the same database or shared Storage bucket until tenant isolation is implemented and tested.

## Verified safeguards already present

- RLS is enabled on the inspected application tables.
- The textbook bucket `ruven-school-files` is private, has a 20 MiB limit, and allows PDF MIME types.
- Textbook uploads are admin-gated and approved catalogue entries are checked before student downloads.
- Study records are student-owned for student reads/inserts; admin reporting is role-gated.
- The client uses a publishable key; the service-role key must remain server-side.
- The latest branch-head CI run completed successfully, including unit tests, release APK assembly, and APK signature verification.

These controls are useful, but they do not substitute for tenant isolation.

## Required tenant architecture before multi-school launch

### 1. Explicit school identity

Introduce `public.schools` with immutable UUID primary keys and lifecycle/status fields. Add a non-null school membership relation for every authenticated profile. Prefer a separate `school_memberships` table if a user may legitimately belong to more than one school; otherwise a non-null `profiles.school_id` is simpler. The product decision must be explicit before migration.

A trusted provisioning flow must assign membership. Never accept a school ID supplied by an ordinary client as proof of membership, and never use user-editable JWT metadata for authorization.

### 2. Tenant key on every school-owned row

Add `school_id` to every school-owned table listed above, including records whose ownership is currently inferred through a parent row. Use composite constraints where practical, such as a unique key on `(school_id, id)` and composite foreign keys to prevent cross-school references. User preferences may remain user-scoped if they contain no school-owned data.

### 3. RLS and helper redesign

Replace role-only checks with helpers that verify both:
- the caller is an active member of the row's school; and
- the caller's role grants the requested operation within that same school.

For child rows, verify the referenced parent belongs to the same `school_id`. Apply the same rule to profile directories, roster, reports, decisions, leave requests, textbook catalog, study records, notifications, teacher assignments, and seals. Audit every permissive policy because PostgreSQL combines permissive policies with OR semantics.

### 4. Storage tenant boundaries

Use tenant-scoped object paths such as `schools/<school_uuid>/textbooks/<object>`. Storage policies must derive the school UUID from the path and verify active membership plus role. Catalogue metadata and object paths must agree on the same school. Keep the bucket private.

### 5. Safe data migration

Do not add a default school UUID or fabricate a school record. Before backfill:
1. Identify the real existing school and have an authorized owner confirm its identity.
2. Snapshot and reconcile all existing profiles, roster rows, assignments, content, reports, decisions, leave requests, seals, and storage objects.
3. Resolve orphaned rows and ambiguous records.
4. Backfill the confirmed school UUID in a transaction.
5. Add NOT NULL, foreign-key, and uniqueness constraints only after validation.
6. Deploy tenant-aware RLS and storage policies before allowing additional memberships.
7. Test cross-school access with at least two isolated test tenants and negative tests for every table and storage operation.

### 6. Operational checks

- Enable Supabase Auth leaked-password protection in the Auth dashboard; the current security advisor reports it disabled.
- **Completed 2026-11-21:** added `student_roster(created_by)` index after confirming its FK.
- **Completed 2026-11-21:** split owner roster write policies, consolidated roster SELECT authorization, and wrapped `auth.uid()` in a scalar subquery. The performance advisor no longer reports the roster FK as unindexed or the roster auth initplan issue.
- Remaining: review duplicate/permissive policies on other tables and ensure each UPDATE policy has both `USING` and `WITH CHECK`.
- Run Supabase security/performance advisors after each schema change.

## Rollout gates

| Gate | Required evidence |
|---|---|
| Tenant model approved | Product decision on single-school vs multi-membership |
| Data reconciled | Counts and orphan checks for every school-owned table |
| RLS complete | Positive and cross-tenant negative tests for every table |
| Storage isolated | Cross-tenant download/upload/delete tests |
| App compatible | School membership loaded from trusted server-side data |
| Release approved | CI success plus security advisor review |

## Current decision

**Do not implement a partial `school_id` column migration yet.** Doing so without a confirmed school identity, full table inventory, parent-child constraints, and coordinated RLS/Storage updates would create a false sense of isolation and could expose existing school data. This audit records the verified blocker and the safe implementation sequence.
