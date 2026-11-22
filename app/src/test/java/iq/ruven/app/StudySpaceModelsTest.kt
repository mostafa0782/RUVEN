package iq.ruven.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StudySpaceModelsTest {
    @Test fun glbHeaderAcceptsVersionTwoAndReturnsDeclaredLength() {
        val header = byteArrayOf(
            0x67, 0x6c, 0x54, 0x46,
            0x02, 0x00, 0x00, 0x00,
            0x18, 0x00, 0x00, 0x00
        )
        assertEquals(24L, glbDeclaredLength(header))
    }

    @Test fun glbHeaderRejectsWrongMagicVersionAndTruncatedHeader() {
        assertEquals(null, glbDeclaredLength(byteArrayOf(0x7b, 0x22, 0x61, 0x22)))
        assertEquals(null, glbDeclaredLength(byteArrayOf(0x67, 0x6c, 0x54)))
        assertEquals(null, glbDeclaredLength(byteArrayOf()))
        assertEquals(null, glbDeclaredLength(byteArrayOf(
            0x67, 0x6c, 0x54, 0x46,
            0x01, 0x00, 0x00, 0x00,
            0x18, 0x00, 0x00, 0x00
        )))
    }

    @Test fun glbHeaderRejectsDeclaredLengthOverLimit() {
        assertEquals(null, glbDeclaredLength(byteArrayOf(
            0x67, 0x6c, 0x54, 0x46,
            0x02, 0x00, 0x00, 0x00,
            0x01, 0x00, 0x00, 0x02
        )))
    }

    @Test fun focusAndBreakCycleTransitionsAreCalculated() {
        assertEquals(StudyCycleState(StudyPhase.FOCUS, 60L), studyCycleState(24 * 60L, StudyInterval.POMODORO_25_5))
        assertEquals(StudyCycleState(StudyPhase.BREAK, 5 * 60L), studyCycleState(25 * 60L, StudyInterval.POMODORO_25_5))
        assertEquals(StudyCycleState(StudyPhase.BREAK, 2 * 60L), studyCycleState(28 * 60L, StudyInterval.POMODORO_25_5))
        assertEquals(StudyCycleState(StudyPhase.FOCUS, 25 * 60L), studyCycleState(30 * 60L, StudyInterval.POMODORO_25_5))
    }

    @Test fun focusedSecondsExcludeBreaksAndCountPartialCycles() {
        assertEquals(25L * 60L, studyFocusSeconds(30L * 60L, StudyInterval.POMODORO_25_5))
        assertEquals(25L * 60L + 10L * 60L, studyFocusSeconds(40L * 60L, StudyInterval.POMODORO_25_5))
        assertEquals(50L * 60L, studyFocusSeconds(60L * 60L, StudyInterval.LONG_50_10))
        assertEquals(17L * 60L, studyFocusSeconds(17L * 60L, StudyInterval.NO_BREAKS))
    }

    @Test(expected = IllegalArgumentException::class)
    fun focusSecondsRejectNegativeElapsedTime() {
        studyFocusSeconds(-1L, StudyInterval.POMODORO_25_5)
    }

    @Test fun longFocusCycleAndNoBreakModeWork() {
        assertEquals(StudyCycleState(StudyPhase.BREAK, 10 * 60L), studyCycleState(50 * 60L, StudyInterval.LONG_50_10))
        assertEquals(StudyCycleState(StudyPhase.NO_BREAK, 0L), studyCycleState(3600L, StudyInterval.NO_BREAKS))
    }

    @Test(expected = IllegalArgumentException::class)
    fun focusCycleRejectsNegativeElapsedTime() {
        studyCycleState(-1L, StudyInterval.POMODORO_25_5)
    }

    @Test fun supportsFourHourAndUnlimitedSessions() {
        assertEquals(240, StudyDuration.HOURS_4.minutes)
        assertEquals(0, StudyDuration.UNLIMITED.minutes)
        assertTrue(StudySessionConfig("رياضيات").hasTimerLimit)
        assertFalse(StudySessionConfig("رياضيات", duration = StudyDuration.UNLIMITED).hasTimerLimit)
    }

    @Test fun pageTrackingIsOptionalAndDoesNotRequirePageNumbers() {
        assertEquals(OptionalPageProgress(), OptionalPageProgress(enabled = false))
        assertEquals(12, OptionalPageProgress(enabled = true, lastPage = 12, totalPages = 80).lastPage)
    }

    @Test(expected = IllegalArgumentException::class)
    fun disabledPageTrackingRejectsStoredPages() {
        OptionalPageProgress(enabled = false, lastPage = 3)
    }

    @Test fun reportCountsUniqueBooksAndAllFourHomeworkStates() {
        val activity = listOf(
            StudyActivityRecord("s1", "رياضيات", "المعادلات", "كتاب الرياضيات", 1800,
                LearningProgress.COMPLETED, recordedAt = Instant.parse("2026-11-21T10:00:00Z")),
            StudyActivityRecord("s1", "رياضيات", "الدوال", "كتاب الرياضيات", 900,
                LearningProgress.IN_PROGRESS, recordedAt = Instant.parse("2026-11-21T11:00:00Z")),
            StudyActivityRecord("s1", "علوم", "الخلية", "كتاب العلوم", 600,
                LearningProgress.COMPLETED, recordedAt = Instant.parse("2026-11-21T12:00:00Z"))
        )
        val homework = HomeworkProgress.values().mapIndexed { index, status ->
            HomeworkStatusRecord("s1", "رياضيات", "واجب $index", status)
        }
        val report = StudentStudyReport.build(activity, homework)
        assertEquals(2, report.lessonsCompleted)
        assertEquals(2, report.booksStudied)
        assertEquals(3300L, report.studySeconds)
        assertEquals(3, report.sessions)
        assertEquals(1, report.homeworkWritten)
        assertEquals(1, report.homeworkCompleted)
        assertEquals(1, report.homeworkPartiallyCompleted)
        assertEquals(1, report.homeworkNotCompleted)
    }

    @Test fun examScopeKeepsExamKindAndExactLesson() {
        val marker = ExamScopeMarker(ExamKind.MIDYEAR, "الفصل الأول", "الفصل الثاني", "الدرس الثالث")
        assertEquals(ExamKind.MIDYEAR, marker.kind)
        assertEquals("الدرس الثالث", marker.lesson)
    }
}
