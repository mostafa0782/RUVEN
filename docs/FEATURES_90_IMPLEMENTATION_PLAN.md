# RUVEN — 90-Feature Product Implementation Plan

**Status:** Product specification and implementation backlog. These features are NOT all implemented yet. Each item must move through Proposed → Designed → Implemented → Tested → Pilot-approved before being described as released.

## Product principles
- Student-first usability, teacher-reviewed workflows, admin-reviewed operations, and official-rule verification for ministry-facing workflows.
- Arabic-first, Iraqi-friendly wording for student-facing guidance; clear formal Arabic for official records and administration. Provide English localization where needed.
- Never invent official leave allowances, grading rules, ministry procedures, or legal requirements. School-configurable rules must be approved by authorized staff.
- Private by default for mood, social, family, and personal notes. No public student rankings or forced social participation.
- Explicit consent before sharing medical documents, location, voice recordings, photos, or personal reports.
- Server-side authorization for sensitive operations; never trust a role or school ID supplied only by the client.
- Keep tenant isolation, auditability, accessibility, low-bandwidth support, and offline/error states in the architecture from the start.

## Phase 1 — Student daily flow and study (1–15)
1. **Tomorrow's bag checklist:** generate a checklist from the published timetable and assigned homework; let the student confirm or customize items.
2. **Homework breakdown:** split a task into editable 5–15 minute steps; student controls estimates and completion.
3. **Spaced-repetition reminders:** optional review reminders at configurable intervals such as day 1, 3, and 7.
4. **Focus mode:** temporarily show only the selected task, its deadline, and essential navigation; allow exit at any time.
5. **Study hint ladder:** provide a small hint, then a second hint, then an explanation; do not pretend an unverified answer is official.
6. **Homework time estimate:** estimate from the student's own past estimates, clearly labeled as approximate.
7. **Printable revision sheet:** format selected published material into a clean print/export view, respecting file permissions.
8. **Read-aloud mode:** Android text-to-speech for supported text with speed controls and accessibility labels.
9. **Exam and deadline planner:** calendar of published exams, homework, and personal study sessions.
10. **Final-grade calculator:** calculate scenarios only from user-entered or officially published weights; label estimates and never present them as official grades.
11. **Quick voice note:** record a note only after permission, show recording state, and offer playback/delete; transcription is optional and consent-based.
12. **Handwriting/photo capture:** attach a page image to a personal note; OCR is optional, reviewed by the student, and never silently published.
13. **Saved content:** bookmark preparations, homework, announcements, and useful resources.
14. **Recently viewed:** locally or securely record recent content with a clear clear-history action.
15. **Weekly student recap:** private summary of completed tasks, upcoming deadlines, and study sessions; no public comparison.

## Phase 2 — Morning, schedule, and belongings (16–25)
16. **Countdown to next lesson:** show next lesson from the current published timetable and the device's local time.
17. **Break timer:** optional, configurable timer with discreet notification and no forced sound.
18. **15-minute morning plan:** quick checklist generated from the day's timetable and due work.
19. **Weather-aware reminder:** only when a weather source is configured; show source/time and avoid medical or certainty claims.
20. **Night-before preparation:** suggest three things to prepare based on tomorrow's timetable and homework.
21. **Departure reminder:** user-configured travel buffer and reminder; do not track location continuously by default.
22. **Adaptive alarm suggestion:** suggest a wake-up time from user-entered preparation and travel durations; do not silently create alarms.
23. **Lost-item QR label:** generate a QR code with a random claim token, not a student's private profile or phone number.
24. **Lost-and-found board:** upload item photos with moderation, limited retention, and no public display of personal data.
25. **Book and equipment loan log:** record borrower, item, due date, and return status with role-based access.

## Phase 3 — Absence, wellbeing, and family (26–35)
26. **Leave request tracking:** submit and follow pending/approved/rejected status; the school's authorized staff make decisions.
27. **What I missed:** summarize published homework and announcements for a date/absence, without claiming to know unrecorded classroom activity.
28. **Absence policy display:** show only the current policy entered and approved by school administrators; no invented absence allowance.
29. **Sick-day quiet mode:** reduce nonessential notifications and surface published classwork; user can switch it off.
30. **Medical attachment flow:** attach a report only after explicit confirmation, restrict access, and set retention according to approved policy.
31. **Private mood check-in:** optional emoji-based personal log; default private, deletable, and not visible to teachers/parents without a clearly explained opt-in.
32. **Quiet minute:** optional short breathing/rest prompt, without diagnostic or therapeutic claims.
33. **Eye-break reminder:** user-controlled reminders for screen breaks with snooze/disable options.
34. **End-of-day quiet mode:** schedule a notification pause; keep critical school notices according to user settings and policy.
35. **Family arrival button:** share an arrival status only after the student explicitly taps it and chooses recipients; no background tracking.

## Phase 4 — Kind communication and motivation (36–45)
36. **Thank-you helper:** templates for a respectful thank-you message; user reviews before sending.
37. **Apology and message helper:** suggest calm wording without sending automatically.
38. **Optional birthdays:** birthdays are private/opt-in and shown only when the person consents.
39. **Study partner matching:** opt-in matching by subject/time, with safe reporting and no exposure of personal contact details.
40. **Encouragement notes:** moderated, optionally anonymous messages; abuse reporting and rate limits required.
41. **Help received log:** private note to remember who helped, with optional reminder to thank them.
42. **Class achievements:** celebrate shared class milestones, not individual public rankings or sensitive performance.
43. **Share notes safely:** share selected notes with a chosen audience and expiry; prevent accidental school-wide publication.
44. **Word or fact of the day:** curated or reviewed educational content with source attribution when relevant.
45. **Weekly recap card:** generate a shareable card only after preview; exclude private data by default.

## Phase 5 — Teacher workspace (46–58)
46. **Teacher home dashboard:** assigned classes, subjects, pending drafts, and today's published timetable.
47. **Lesson-preparation templates:** reusable teacher-owned templates with subject and grade tags.
48. **Homework templates:** reusable instructions, deadlines, attachments, and target sections.
49. **Draft-preview-publish workflow:** posts remain drafts until the authorized teacher/admin publishes them.
50. **Scheduled publishing:** schedule supported posts with timezone clarity, cancellation, and audit history.
51. **Teacher task checklist:** private workflow checklist for preparing lessons, marking, and follow-up.
52. **Classwork attachments:** validate file type/size, show upload progress, and support retry after network errors.
53. **Assignment status overview:** show submitted/not-yet-submitted counts only where the school has authorized and configured this workflow.
54. **Attendance correction request:** corrections require authorized approval and append-only audit history; never silently overwrite attendance.
55. **Student support note:** restricted, purpose-limited notes with explicit retention and no broad directory exposure.
56. **Parent/guardian message templates:** school-approved templates with preview and recipient confirmation.
57. **Teacher workload view:** personal calendar of classes, due work, and scheduled posts; no staff ranking.
58. **Teacher feedback channel:** report app problems or request features without including student records by default.

## Phase 6 — School administration and supervision (59–70)
59. **Administrative dashboard:** pending leave requests, unpublished notices, timetable issues, and actionable alerts by permission.
60. **School calendar management:** create and publish events with audience, dates, and change history.
61. **Timetable conflict checker:** detect overlapping lessons/rooms/sections and explain conflicts before publication.
62. **Attendance audit:** filter authorized attendance changes by date, actor, and reason; restrict access and record every sensitive view/change as appropriate.
63. **Leave decision workflow:** authorized approver, decision reason, student notification, and immutable event history.
64. **Decision lifecycle:** draft, review, approve, issue, acknowledge, execute, and archive, with role checks at each step.
65. **Seal registry:** owner, role, permitted document types, validity, revocation, and verification metadata; a visual seal is not automatically a legally valid digital signature.
66. **Daily report workflow:** draft, submit, review, publish, and archive with timestamps and permissions.
67. **Staff directory privacy:** expose only fields needed for the viewer's role; never make private phone numbers public by default.
68. **Roster import validation:** preview rows, detect likely duplicates, validate section/grade, and produce an error report before commit.
69. **Supervisor visit/report log:** configurable records for visits, observations, recommendations, follow-up owner, and due date; official forms require local verification.
70. **Administrative export center:** export only authorized fields, log the export, confirm purpose, and avoid uncontrolled bulk downloads.

## Phase 7 — Ministry and multi-school readiness (71–80)
71. **Multi-school tenant isolation:** every school-owned record must be scoped server-side to an immutable school identifier; cross-school tests are release-blocking.
72. **School onboarding and verification:** verify a school's identity and authorized administrator before activating its tenant.
73. **Academic-year rollover:** archive/close an academic year and initialize the next one without accidentally mixing rosters, classes, or reports.
74. **Configurable school rules:** approved school settings for grading labels, workflow steps, and notifications; never silently override official regulations.
75. **Ministry circular register:** store source, issuing authority, reference number, issue date, effective date, and attachment; label unverified documents clearly.
76. **Policy version history:** track which approved rule was active when a workflow occurred and who changed the configuration.
77. **Aggregate reports:** produce school/district-level summaries only for authorized users, with minimum-group safeguards to reduce re-identification.
78. **Official data export format:** configurable export mapping reviewed against the receiving authority's current specification before use.
79. **Ministry integration boundary:** API integration only with written authorization, documented schemas, least privilege, and sandbox testing; no scraping or bypassing access controls.
80. **Official compliance checklist:** track review evidence for privacy, security, accessibility, retention, accessibility, and official approval; never display “ministry approved” without written approval.

## Phase 8 — Platform reliability, privacy, and operations (81–90)
81. **Granular notification controls:** category preferences, quiet hours, and batching, with critical-message behavior documented.
82. **Offline-friendly reading:** cache only appropriate published content, display last-updated time, and clearly mark unsent actions.
83. **Reliable retry and sync:** idempotent retries, duplicate prevention, visible failure state, and safe conflict handling.
84. **Security event audit:** log sensitive administrative actions with actor, timestamp, target, and outcome; protect logs from client modification.
85. **Privacy and retention center:** explain data use, retention, access, export, and deletion workflows in clear language.
86. **Account safety:** secure session handling, sign-out from lost devices where supported, rate limits, and safe account recovery.
87. **Backup and restore drills:** documented recovery objectives, encrypted backups, and periodic restore tests.
88. **Crash and performance monitoring:** minimize collected data, redact secrets and student content, and restrict diagnostic access.
89. **Release quality gates:** automated tests, dependency checks, staged rollout, release notes, and rollback procedure.
90. **School pilot scorecard:** measure task completion, reliability, support burden, accessibility feedback, and user satisfaction without invasive tracking.

## Recommended implementation order
1. **First release:** 1, 2, 3, 4, 13, 16, 18, 20, 26, 27, 46, 49, 59, 61, 63, 81, 82, 83, 89.
2. **Second release:** teacher templates, school calendar, import validation, safe lost-and-found, and weekly recap.
3. **Third release:** supervision workflows, school-year rollover, policy versioning, approved exports, and pilot reporting.
4. **Advanced / approval-dependent:** medical records, student support notes, location sharing, ministry integrations, and any legally significant digital signature.

## Definition of done for each feature
- User story and role/permission matrix documented.
- UI includes loading, empty, success, error, offline, and retry states where applicable.
- Server-side validation and authorization implemented for protected data/actions.
- Automated tests cover expected behavior, forbidden roles, and cross-school access where relevant.
- Arabic/RTL, accessibility, and tablet layouts reviewed.
- Privacy, retention, consent, and abuse cases reviewed.
- Changes tested in a non-production environment before release.
- Feature status and known limitations documented honestly.

## Important boundary
This file is a roadmap, not evidence that the listed features have been coded, tested, or approved. Ministry-facing workflows and legally sensitive records require review against current Iraqi rules and written authorization from the relevant authority.
