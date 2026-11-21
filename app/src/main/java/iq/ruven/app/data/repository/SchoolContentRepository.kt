package iq.ruven.app.data.repository

import iq.ruven.app.data.model.Homework
import iq.ruven.app.data.model.Lesson
import iq.ruven.app.data.model.SchoolAnnouncement

/**
 * Boundary between UI/domain code and an authenticated school data source.
 *
 * Identity and school membership must be derived from the authenticated session,
 * not supplied as arbitrary studentId/schoolId parameters by the UI. The backend
 * must still authorize every request and scope it to that session.
 */
interface SchoolContentRepository {
    suspend fun getLessons(weekday: Int): Result<List<Lesson>>

    suspend fun getHomework(): Result<List<Homework>>

    suspend fun getAnnouncements(): Result<List<SchoolAnnouncement>>
}
