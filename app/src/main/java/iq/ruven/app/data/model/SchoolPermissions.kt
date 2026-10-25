package iq.ruven.app.data.model

/** Role names mirror public.school_role in Supabase. Server-side RLS remains authoritative. */
enum class SchoolRole {
    STUDENT,
    CLASS_MONITOR,
    PARLIAMENT_MEMBER,
    TEACHER,
    CLASS_SUPERVISOR,
    ASSISTANT_PRINCIPAL,
    PRINCIPAL,
    EDUCATIONAL_COUNSELOR,
    MINISTRY_EDUCATION_MEMBER,
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
            SchoolRole.ASSISTANT_PRINCIPAL, SchoolRole.PRINCIPAL, SchoolRole.OWNER -> true
            SchoolRole.STUDENT,
            SchoolRole.CLASS_MONITOR,
            SchoolRole.PARLIAMENT_MEMBER,
            SchoolRole.CLASS_SUPERVISOR,
            SchoolRole.EDUCATIONAL_COUNSELOR,
            SchoolRole.MINISTRY_EDUCATION_MEMBER -> false
        }

    fun canEditOrDeleteHomework(role: SchoolRole): Boolean =
        role == SchoolRole.PRINCIPAL || role == SchoolRole.ASSISTANT_PRINCIPAL || role == SchoolRole.OWNER

    fun canManageTimetable(role: SchoolRole): Boolean =
        role == SchoolRole.CLASS_MONITOR ||
            role == SchoolRole.ASSISTANT_PRINCIPAL ||
            role == SchoolRole.PRINCIPAL ||
            role == SchoolRole.OWNER

    fun canUploadTimetableFile(role: SchoolRole): Boolean = canManageTimetable(role)

    fun canCreatePreparation(role: SchoolRole, assignedSubject: Boolean = false): Boolean =
        (role == SchoolRole.TEACHER && assignedSubject) || role == SchoolRole.OWNER

    fun canReportToPrincipal(role: SchoolRole): Boolean =
        role == SchoolRole.CLASS_MONITOR ||
            role == SchoolRole.PARLIAMENT_MEMBER ||
            role == SchoolRole.EDUCATIONAL_COUNSELOR

    fun canPublishAnnouncements(role: SchoolRole): Boolean =
        role == SchoolRole.PRINCIPAL || role == SchoolRole.ASSISTANT_PRINCIPAL || role == SchoolRole.OWNER
}
