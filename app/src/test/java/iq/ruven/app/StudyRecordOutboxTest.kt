package iq.ruven.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class StudyRecordOutboxTest {
    @Test fun draftRoundTripsAllOptionalAndRequiredFields() {
        val draft = StudyRecordDraft(
            recordType = "homework_status",
            subjectName = "الرياضيات",
            chapterName = "الفصل الأول",
            lessonName = "المعادلات",
            homeworkTitle = "حل التمارين",
            homeworkStatus = "شبه منجز",
            pageNumber = 12
        )
        val restored = StudyRecordDraft.fromJson(draft.toJson("student-1"))
        assertEquals(draft, restored)
        assertEquals("student-1", draft.toJson("student-1").getString("user_id"))
    }

    @Test fun draftKeepsNullsAsJsonNullRatherThanTheStringNull() {
        val draft = StudyRecordDraft(recordType = "study_session", subjectName = "العلوم")
        val json = draft.toJson("student-1")
        assertTrue(json.isNull("chapter_name"))
        assertTrue(json.isNull("lesson_name"))
        assertTrue(json.isNull("page_number"))
        assertFalse(json.optString("chapter_name") == "null")
    }

    @Test(expected = IllegalArgumentException::class)
    fun draftRejectsUnsupportedRecordTypes() {
        StudyRecordDraft(recordType = "unknown", subjectName = "العلوم")
    }

    @Test(expected = IllegalArgumentException::class)
    fun draftRejectsInvalidClientRecordId() {
        StudyRecordDraft(recordType = "study_session", subjectName = "العلوم", clientRecordId = "not-a-uuid")
    }
}
