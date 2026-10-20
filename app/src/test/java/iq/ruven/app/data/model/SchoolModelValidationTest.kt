package iq.ruven.app.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolModelValidationTest {
    @Test
    fun validLessonHasNoValidationErrors() {
        val lesson = Lesson(
            id = "lesson-1",
            subject = "الرياضيات",
            startsAt = "08:00",
            endsAt = "08:45",
            weekday = 1
        )

        assertTrue(SchoolModelValidation.validate(lesson).isEmpty())
    }

    @Test
    fun lessonRejectsInvalidWeekdayAndReversedTimes() {
        val lesson = Lesson(
            id = "lesson-1",
            subject = "الرياضيات",
            startsAt = "09:00",
            endsAt = "08:00",
            weekday = 8
        )

        assertEquals(
            listOf("weekday must be in 1..7", "startsAt must be before endsAt"),
            SchoolModelValidation.validate(lesson)
        )
    }

    @Test
    fun homeworkRejectsBlankTitleAndMalformedDueDate() {
        val homework = Homework(
            id = "homework-1",
            subject = "الكيمياء",
            title = " ",
            dueDate = "2026-02-30"
        )

        assertEquals(
            listOf("title must not be blank", "dueDate must be a valid ISO date"),
            SchoolModelValidation.validate(homework)
        )
    }

    @Test
    fun announcementRequiresAnInstantAndNonBlankContent() {
        val announcement = SchoolAnnouncement(
            id = "notice-1",
            title = "",
            body = " ",
            publishedAt = "tomorrow"
        )

        assertEquals(
            listOf(
                "title must not be blank",
                "body must not be blank",
                "publishedAt must be a valid ISO-8601 instant"
            ),
            SchoolModelValidation.validate(announcement)
        )
    }
}
