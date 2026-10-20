package iq.ruven.app.data.repository

import iq.ruven.app.data.model.Homework
import iq.ruven.app.data.model.Lesson
import iq.ruven.app.data.model.SchoolAnnouncement
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatedSchoolContentRepositoryTest {
    @Test
    fun validRecordsPassThroughUnchanged() = runTest {
        val lesson = Lesson(
            id = "l1",
            subject = "اللغة العربية",
            startsAt = "08:00",
            endsAt = "08:45",
            weekday = 1
        )
        val repository = ValidatedSchoolContentRepository(
            FakeRepository(lessons = listOf(lesson))
        )

        assertEquals(Result.success(listOf(lesson)), repository.getLessons("school", "student", 1))
    }

    @Test
    fun malformedRecordsReturnFailureInsteadOfBeingRendered() = runTest {
        val repository = ValidatedSchoolContentRepository(
            FakeRepository(lessons = listOf(
                Lesson(
                    id = "l1",
                    subject = "الرياضيات",
                    startsAt = "09:00",
                    endsAt = "08:00",
                    weekday = 1
                )
            ))
        )

        val result = repository.getLessons("school", "student", 1)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidSchoolContentException)
        assertEquals(
            listOf("record[0]: startsAt must be before endsAt"),
            (result.exceptionOrNull() as InvalidSchoolContentException).validationErrors
        )
    }

    private class FakeRepository(
        private val lessons: List<Lesson> = emptyList(),
        private val homework: List<Homework> = emptyList(),
        private val announcements: List<SchoolAnnouncement> = emptyList()
    ) : SchoolContentRepository {
        override suspend fun getLessons(
            schoolId: String,
            studentId: String,
            weekday: Int
        ) = Result.success(lessons)

        override suspend fun getHomework(schoolId: String, studentId: String) =
            Result.success(homework)

        override suspend fun getAnnouncements(schoolId: String) =
            Result.success(announcements)
    }
}
