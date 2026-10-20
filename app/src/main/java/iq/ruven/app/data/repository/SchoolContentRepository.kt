package iq.ruven.app.data.repository

import iq.ruven.app.data.model.Homework
import iq.ruven.app.data.model.Lesson
import iq.ruven.app.data.model.SchoolAnnouncement

/**
 * Boundary between UI/domain code and the future authenticated data source.
 *
 * Implementations must scope every query to the authenticated user's school and
 * permissions. A schoolId supplied by a client is not an authorization check.
 * No in-memory/demo implementation is provided, so the UI cannot mistake sample
 * content for official school information.
 */
interface SchoolContentRepository {
    suspend fun getLessons(
        schoolId: String,
        studentId: String,
        weekday: Int
    ): Result<List<Lesson>>

    suspend fun getHomework(
        schoolId: String,
        studentId: String
    ): Result<List<Homework>>

    suspend fun getAnnouncements(
        schoolId: String
    ): Result<List<SchoolAnnouncement>>
}
