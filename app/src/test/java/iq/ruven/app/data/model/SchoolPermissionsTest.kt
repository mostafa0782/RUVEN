package iq.ruven.app.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolPermissionsTest {
    @Test
    fun teachersCanPostOnlyWhenTheirSubjectIsAssigned() {
        assertFalse(SchoolPermissions.canCreateHomework(SchoolRole.TEACHER))
        assertFalse(SchoolPermissions.canCreatePreparation(SchoolRole.TEACHER))
        assertTrue(SchoolPermissions.canCreateHomework(SchoolRole.TEACHER, assignedSubject = true))
        assertTrue(SchoolPermissions.canCreatePreparation(SchoolRole.TEACHER, assignedSubject = true))
    }

    @Test
    fun monitorCanManageTimetableAndReportButCannotDeleteHomework() {
        assertTrue(SchoolPermissions.canManageTimetable(SchoolRole.CLASS_MONITOR))
        assertTrue(SchoolPermissions.canUploadTimetableFile(SchoolRole.CLASS_MONITOR))
        assertTrue(SchoolPermissions.canReportToPrincipal(SchoolRole.CLASS_MONITOR))
        assertFalse(SchoolPermissions.canEditOrDeleteHomework(SchoolRole.CLASS_MONITOR))
    }

    @Test
    fun principalAndAssistantsCanManageTimetableAndModerateHomework() {
        for (role in listOf(SchoolRole.PRINCIPAL, SchoolRole.ASSISTANT_PRINCIPAL)) {
            assertTrue(SchoolPermissions.canManageTimetable(role))
            assertTrue(SchoolPermissions.canEditOrDeleteHomework(role))
        }
    }

    @Test
    fun onlySchoolLeadershipCanPublishAnnouncements() {
        assertTrue(SchoolPermissions.canPublishAnnouncements(SchoolRole.PRINCIPAL))
        assertTrue(SchoolPermissions.canPublishAnnouncements(SchoolRole.ASSISTANT_PRINCIPAL))
        assertFalse(SchoolPermissions.canPublishAnnouncements(SchoolRole.TEACHER))
        assertFalse(SchoolPermissions.canPublishAnnouncements(SchoolRole.CLASS_MONITOR))
        assertFalse(SchoolPermissions.canPublishAnnouncements(SchoolRole.STUDENT))
    }

    @Test
    fun studentsCannotPublishSchoolContent() {
        assertFalse(SchoolPermissions.canCreateHomework(SchoolRole.STUDENT))
        assertFalse(SchoolPermissions.canCreatePreparation(SchoolRole.STUDENT))
        assertFalse(SchoolPermissions.canManageTimetable(SchoolRole.STUDENT))
    }
}
