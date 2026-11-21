package iq.ruven.app.data.model

/**
 * Domain models for school content. These models intentionally contain no sample
 * records: production values must come from an authenticated, school-scoped source.
 */
data class Lesson(
    val id: String,
    val subject: String,
    val teacherName: String? = null,
    val room: String? = null,
    /** ISO-8601 local time, for example "08:00". */
    val startsAt: String,
    /** ISO-8601 local time, for example "08:45". */
    val endsAt: String,
    /** ISO weekday: Monday = 1 ... Sunday = 7. */
    val weekday: Int
)

data class Homework(
    val id: String,
    val subject: String,
    val title: String,
    val description: String? = null,
    /** ISO-8601 date, for example "2026-10-25"; null when no deadline was set. */
    val dueDate: String? = null,
    val isCompleted: Boolean = false
)

enum class AnnouncementPriority {
    NORMAL,
    IMPORTANT
}

data class SchoolAnnouncement(
    val id: String,
    val title: String,
    val body: String,
    /** ISO-8601 timestamp supplied by the trusted data source. */
    val publishedAt: String,
    val priority: AnnouncementPriority = AnnouncementPriority.NORMAL
)
