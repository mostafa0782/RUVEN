package iq.ruven.app.data.model

/** A lesson preparation authored by a teacher for their assigned subject. */
data class Preparation(
    val id: String,
    val subject: String,
    val title: String,
    val content: String,
    val teacherId: String,
    /** ISO-8601 local date, for example "2026-10-25". */
    val lessonDate: String? = null
)
