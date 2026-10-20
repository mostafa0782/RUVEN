package iq.ruven.app.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

/**
 * Validates untrusted records received from a future API before they are rendered.
 * This is data-quality validation, not authorization or proof that content is official.
 */
object SchoolModelValidation {
    fun validate(lesson: Lesson): List<String> = buildList {
        if (lesson.id.isBlank()) add("id must not be blank")
        if (lesson.subject.isBlank()) add("subject must not be blank")
        if (lesson.weekday !in 1..7) add("weekday must be in 1..7")

        val start = parseTime(lesson.startsAt)
        val end = parseTime(lesson.endsAt)
        if (start == null) add("startsAt must be a valid ISO local time")
        if (end == null) add("endsAt must be a valid ISO local time")
        if (start != null && end != null && !start.isBefore(end)) {
            add("startsAt must be before endsAt")
        }
    }

    fun validate(homework: Homework): List<String> = buildList {
        if (homework.id.isBlank()) add("id must not be blank")
        if (homework.subject.isBlank()) add("subject must not be blank")
        if (homework.title.isBlank()) add("title must not be blank")
        if (homework.dueDate != null && parseDate(homework.dueDate) == null) {
            add("dueDate must be a valid ISO date")
        }
    }

    fun validate(announcement: SchoolAnnouncement): List<String> = buildList {
        if (announcement.id.isBlank()) add("id must not be blank")
        if (announcement.title.isBlank()) add("title must not be blank")
        if (announcement.body.isBlank()) add("body must not be blank")
        if (parseInstant(announcement.publishedAt) == null) {
            add("publishedAt must be a valid ISO-8601 instant")
        }
    }

    private fun parseTime(value: String): LocalTime? = try {
        LocalTime.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseDate(value: String): LocalDate? = try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun parseInstant(value: String): Instant? = try {
        Instant.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}
