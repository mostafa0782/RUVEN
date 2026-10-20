package iq.ruven.app.data.repository

import iq.ruven.app.data.model.Homework
import iq.ruven.app.data.model.Lesson
import iq.ruven.app.data.model.SchoolAnnouncement
import iq.ruven.app.data.model.SchoolModelValidation

/**
 * Decorator that prevents malformed API records from crossing into presentation code.
 * It does not replace server-side validation, authentication, or authorization.
 */
class ValidatedSchoolContentRepository(
    private val delegate: SchoolContentRepository
) : SchoolContentRepository {
    override suspend fun getLessons(
        schoolId: String,
        studentId: String,
        weekday: Int
    ): Result<List<Lesson>> = delegate.getLessons(schoolId, studentId, weekday).mapCatching { lessons ->
        validateRecords(lessons, SchoolModelValidation::validate)
    }

    override suspend fun getHomework(
        schoolId: String,
        studentId: String
    ): Result<List<Homework>> = delegate.getHomework(schoolId, studentId).mapCatching { homework ->
        validateRecords(homework, SchoolModelValidation::validate)
    }

    override suspend fun getAnnouncements(
        schoolId: String
    ): Result<List<SchoolAnnouncement>> =
        delegate.getAnnouncements(schoolId).mapCatching { announcements ->
            validateRecords(announcements, SchoolModelValidation::validate)
        }

    private fun <T> validateRecords(
        records: List<T>,
        validate: (T) -> List<String>
    ): List<T> {
        val errors = records.flatMapIndexed { index, record ->
            validate(record).map { "record[$index]: $it" }
        }
        if (errors.isNotEmpty()) throw InvalidSchoolContentException(errors)
        return records
    }
}

class InvalidSchoolContentException(
    val validationErrors: List<String>
) : IllegalStateException("School content failed validation")
