# RUVEN StudySpace — Product and delivery specification

Status: **Phase 2 implementation in progress**. This document separates delivered behavior from remaining release requirements; a feature is not complete until UI, persistence, authorization, tests, and a pilot are complete.

## Product promise
A single study flow: choose a subject → optionally choose a chapter/lesson → choose a session duration and focus/break pattern → study from the approved textbook → view lesson resources → record progress → see personal and school-authorized reports.

## Session setup
- Match the visual reference supplied by the product owner: RTL bottom sheet, calm teal/green surfaces, large subject selector, clear duration tiles, focus/break segmented control, and one primary start action.
- Durations: 30 minutes, 1, 2, 3, or 4 hours, plus unlimited.
- Focus patterns: 25/5, 50/10, and no breaks. The active phase and remaining phase time are derived from elapsed session time. The student can pause/resume and end at any time.
- Subject is required. Chapter and lesson are optional at session start and can be selected later.
- Do not force a timer or page tracking on students.

## Learning workspace
- Approved PDF textbook catalogue by subject and grade.
- Each edition records title, edition/year, publisher/source, source URL, publication date when known, verification status, and file checksum.
- Only school-admin uploaded or officially sourced/authorized copies are presented as verified. Never label an arbitrary web PDF as the newest edition.
- Preserve edition identity so progress on an older book is not silently mapped to changed page numbers in a new edition.
- Reader restores last position. Page tracking is opt-in; disabling it means no page-based progress is collected.
- Lesson progress states: not started, in progress, completed.
- Lesson-linked 3D resources support rotate/zoom, labels, short explanations, accessible 2D fallback, attribution/licence, and on-demand loading. Do not bundle a large model library in the base APK.
- Exam scope markers attach to exact chapter/lesson and exam kind: monthly, midyear, final, mock, other.

## Homework reporting
The student chooses exactly one status per assigned homework:
- written (كُتب)
- completed (اكتمل)
- partially completed (شبه منجز)
- not completed (لم ينجز)

The teacher may confirm or correct a status with an audit trail. Student self-report and teacher-confirmed status must remain distinguishable. Do not infer homework completion from focus time or PDF page views.

## Reports
Student view: completed lessons, distinct textbooks studied, study time, session count, and counts for each of the four homework states. Optional page counts appear only when page tracking is enabled.

Admin view: individual student and class/section summaries, time range filters, subject breakdown, homework status totals, and export of only authorized fields. Do not create public student rankings. A teacher sees only assigned classes/subjects; school administration sees only its own school.

## Data integrity and privacy requirements
- Server-side identity and school membership determine ownership. Never trust student_id, school_id, role, or class values supplied by the Android client.
- Every exposed table has RLS; policies scope student rows to the authenticated student and report readers to authorized staff in the same school/assigned class.
- Store append-only status-change events (actor, previous state, new state, timestamp, optional reason). A teacher correction must not erase the original self-report.
- Minimize collected data. Page tracking is opt-in and independently switchable. Reports disclose what is visible to the school.
- Offline writes use stable IDs/idempotency keys and visible sync status; never silently duplicate sessions.
- Do not ship service-role credentials in the APK.

## Delivery sequence
1. Domain models and unit tests (session options, optional pages, textbook metadata, lesson progress, exam markers, homework states, report aggregation).
2. Compose setup sheet and active-session screen matching the visual reference.
3. Local persistence and resume-safe session lifecycle.
4. Supabase schema, RLS, audit events, and server-side reporting after confirming school/section membership model.
5. PDF reader integration and edition-aware bookmarks.
6. 3D resource catalogue with licence/attribution and on-demand cache.
7. Admin report UI, export controls, accessibility, offline behavior, and pilot.

## Implementation snapshot

Delivered in the current Android branch:
- Compose StudySpace with subject, optional chapter/lesson, 30-minute through 4-hour and unlimited sessions.
- Pause/resume and active 25/5 or 50/10 focus/rest phase display; no-break mode.
- Local persistence with visible cloud-sync outcomes for student-submitted study, lesson, homework, and exam-scope records.
- Optional page tracking and an in-app PDF reader.
- Private approved-PDF catalogue with administrator upload/review and class-eligible student download.
- Admin study report with student-submitted activity clearly distinguished from verified learning.
- On-demand local GLB viewer using SceneView. It accepts a selected GLB v2 file up to 25 MiB, validates its header/version/declared length, and supports touch orbit/zoom/pan. Temporary files and model nodes are disposed with the viewer.

Still required before calling StudySpace complete:
- Shared 3D resource catalogue, teacher/admin publication workflow, licence/attribution metadata, and accessible 2D fallback. The current GLB viewer is a local-file viewer, not a curriculum catalogue.
- Teacher confirmation/correction with immutable audit events; current homework status is a student self-report.
- Durable offline outbox with idempotency keys and retry; local fallback currently does not guarantee exactly-once synchronization.
- Report date filters, class summaries, assigned-subject enforcement, and integration tests for role/row isolation.
- True multi-school tenant isolation. The current `profiles` schema has no `school_id`; class-name matching alone is not a safe tenant boundary. Do not onboard multiple schools into one shared database until school membership and RLS are implemented and verified across every school-owned table and Storage policy.

## Release gates
- No fabricated textbook edition/source or unlicensed 3D model.
- No page tracking unless the student opts in.
- Four homework states are exact and consistently localized.
- Unit + integration tests for ownership, RLS isolation, duplicate sync, and audit history.
- Admin reports must not leak students across schools or classes.
- Feature is not marked complete based on a design document or domain model alone.
