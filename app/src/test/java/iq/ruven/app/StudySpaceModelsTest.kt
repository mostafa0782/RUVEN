package iq.ruven.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class StudySpaceModelsTest {
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
