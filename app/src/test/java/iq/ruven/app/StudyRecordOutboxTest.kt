package iq.ruven.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

class StudyRecordOutboxTest {
    @Test fun draftCreatesAValidUniqueClientRecordId() {
        val first = StudyRecordDraft(recordType = "study_session", subjectName = "العلوم")
        val second = StudyRecordDraft(recordType = "study_session", subjectName = "العلوم")
        assertEquals(first.clientRecordId, UUID.fromString(first.clientRecordId).toString())
        assertNotEquals(first.clientRecordId, second.clientRecordId)
    }

    @Test fun suppliedClientRecordIdRemainsStableForRetries() {
        val id = UUID.randomUUID().toString()
        val firstAttempt = StudyRecordDraft(
            recordType = "homework_status",
            subjectName = "الرياضيات",
            homeworkTitle = "حل التمارين",
            homeworkStatus = "شبه منجز",
            clientRecordId = id
        )
        val retry = firstAttempt.copy()
        assertEquals(id, firstAttempt.clientRecordId)
        assertEquals(firstAttempt.clientRecordId, retry.clientRecordId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun draftRejectsUnsupportedRecordTypes() {
        StudyRecordDraft(recordType = "unknown", subjectName = "العلوم")
    }

    @Test(expected = IllegalArgumentException::class)
    fun draftRejectsInvalidClientRecordId() {
        StudyRecordDraft(recordType = "study_session", subjectName = "العلوم", clientRecordId = "not-a-uuid")
    }

    @Test(expected = IllegalArgumentException::class)
    fun draftRejectsInvalidDuration() {
        StudyRecordDraft(recordType = "study_session", subjectName = "العلوم", durationSeconds = 86401)
    }
}
