# RUVEN Study Session — Functional Specification

## Goal
Upgrade the existing student study-space timer into a structured study-session workflow. A session is a student-owned record tied to the authenticated account; it is not an attendance record and does not expose study activity to classmates.

## Student flow

### 1. Configure
Required:
- Subject (choose from the student's available subjects; allow a personal/custom subject only if no official subject list is available).
- Planned focus duration.
- Study purpose: exam preparation, homework, revision, or general study.

Optional:
- Session title and a short personal note.
- Break pattern (focus interval and break duration), with a sensible default and editable settings.

For exam preparation:
- Exam subject.
- Exam date/time when known.
- Exam scope / chapters or lessons.
- Teacher notes / emphasized topics.
- These fields must be explicitly entered by the student or loaded from verified published school content. Never fabricate teacher notes or exam dates. Show source/status when populated from school content.

For homework:
- Homework/task reference when a matching published task exists; otherwise a student-entered task title.

### 2. Run
- Clear focus countdown and progress indicator.
- States: READY, FOCUS, BREAK, PAUSED, COMPLETED, CANCELLED.
- Start, pause/resume, skip/end break, and finish early.
- Break notifications are discreet and respect Android notification permission.
- The timer must be based on monotonic elapsed time while the process is alive, not decrement-only mutable state. Persist start/pause timestamps and recover elapsed time after process recreation/reopen.
- Focus time and break time are tracked separately. Paused time is excluded from both.
- Avoid requiring a continuously running foreground service for the first implementation; if background countdown/notifications are added later, implement the appropriate Android foreground-service/notification behavior and lifecycle tests.

### 3. Finish
- Show actual focus duration, break duration, planned duration, completion percentage, subject, purpose, and finish status.
- Early finish is valid and saves actual elapsed focus time, never the planned duration.
- Cancelled sessions are not silently counted as completed study. Preserve an explicit status and allow the student to discard a mistaken session.
- Prevent duplicate report entries on repeated finish taps or app recreation (idempotent save).

## Student report
- Save a private session history associated with the authenticated student.
- Report daily/weekly/overall focused-study totals, session count, subject breakdown, and purpose breakdown.
- Exclude breaks, pauses, and cancelled/discarded sessions from focused-study totals.
- Show local-only / not-synced status if persistence is initially device-local. Do not imply server synchronization until a secure backend table and RLS policies exist.
- Never expose individual student study hours in public class rankings.

## Data model (logical)
StudySession:
- id
- student_id (derived from authenticated identity; never accepted as trusted client-supplied ownership)
- subject_id or subject_name_snapshot
- purpose
- exam metadata (optional: date, scope, teacher notes, source reference)
- planned_focus_seconds
- actual_focus_seconds
- actual_break_seconds
- started_at, ended_at
- status
- created_at

Persist enough timing events/state to recover a running or paused session accurately. Use UTC instants for stored timestamps and device-local timezone only for display.

## Privacy and authorization
- Student sessions are private to their owner by default.
- If server synchronization is introduced, enforce ownership using Supabase Auth identity and RLS; do not rely on client-side filtering.
- Teacher notes and exam information are only considered official when sourced from published/authorized school content. Personal notes remain private.
- Provide deletion for personal session history in accordance with the app's retention design.

## Acceptance criteria
1. Student can select a subject, duration, and purpose before starting.
2. Exam-purpose sessions can capture exam date, scope, and teacher notes without inventing missing data.
3. Focus, break, and pause time are accounted separately and accurately.
4. Timer state survives ordinary recomposition and process recreation without resetting or double-counting.
5. Student can finish early and actual focus duration is saved.
6. A repeated finish action cannot create duplicate records.
7. Reports aggregate only eligible focused-study seconds and remain private to the student.
8. UI has loading, empty, error, and persistence-status states.
9. Unit tests cover elapsed-time accounting, pause/resume, break transitions, early finish, cancellation, and duplicate completion.
