package iq.ruven.app.data.model

/** Roles shown by the UI. These values are not proof of identity; a server must verify them. */
enum class SchoolRole {
    STUDENT,
    CLASS_MONITOR,
    TEACHER,
    ASSISTANT_PRINCIPAL,
    PRINCIPAL,
    OWNER
}

enum class SchoolContentType {
    HOMEWORK,
    TIMETABLE,
    PREPARATION,
    ANNOUNCEMENT
}

object SchoolPermissions {
    fun canCreateHomework(role: SchoolRole, assignedSubject: Boolean = false): Boolean =
        when (role) {
            SchoolRole.TEACHER -> assignedSubject
            SchoolRole.CLASS_MONITOR, SchoolRole.ASSISTANT_PRINCIPAL, SchoolRole.PRINCIPAL, SchoolRole.OWNER -> true
            SchoolRole.STUDENT -> false
        }

    fun canEditOrDeleteHomework(role: SchoolRole): Boolean =
        role == SchoolRole.PRINCIPAL || role == SchoolRole.ASSISTANT_PRINCIPAL || role == SchoolRole.OWNER

    fun canManageTimetable(role: SchoolRole): Boolean =
        role == SchoolRole.CLASS_MONITOR ||
            role == SchoolRole.ASSISTANT_PRINCIPAL ||
            role == SchoolRole.PRINCIPAL || role == SchoolRole.OWNER

    fun canUploadTimetableFile(role: SchoolRole): Boolean = canManageTimetable(role)

    fun canCreatePreparation(role: SchoolRole, assignedSubject: Boolean = false): Boolean =
        role == SchoolRole.TEACHER && assignedSubject

    fun canReportToPrincipal(role: SchoolRole): Boolean = role == SchoolRole.CLASS_MONITOR

    fun canPublishAnnouncements(role: SchoolRole): Boolean =
        role == SchoolRole.PRINCIPAL || role == SchoolRole.ASSISTANT_PRINCIPAL
}
