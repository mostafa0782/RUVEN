package iq.ruven.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class ExamDeadlineTest {
    @Test
    fun parsesSubjectAndIsoDate() {
        val result = parseExamDeadline("Chemistry | 2099-12-03")
        assertEquals("Chemistry", result?.subject)
        assertEquals(LocalDate.of(2099, 12, 3), result?.date)
    }

    @Test
    fun rejectsMissingSubjectOrWrongDateFormat() {
        assertNull(parseExamDeadline(" | 2099-12-03"))
        assertNull(parseExamDeadline("Chemistry | 03-12-2099"))
        assertNull(parseExamDeadline("Chemistry 2099-12-03"))
    }

    @Test
    fun rejectsInvalidAndPastDates() {
        assertNull(parseExamDeadline("Physics | 2099-02-30"))
        assertNull(parseExamDeadline("Physics | 2000-01-01"))
    }
}
