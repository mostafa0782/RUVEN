package iq.ruven.app.data.remote

import android.util.Base64
import iq.ruven.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/**
 * Small Supabase REST/Auth adapter. Never put a service-role key in this Android app.
 * Authentication is required for profile and school-content operations.
 */
class SupabaseBackend(
    private val baseUrl: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/'),
    private val publishableKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
) {
    data class Session(
        val userId: String,
        val accessToken: String,
        val refreshToken: String
    )

    data class Profile(
        val id: String,
        val fullName: String,
        val role: String,
        val className: String?,
        val isOwner: Boolean
    )

    data class ManagedProfile(
        val id: String,
        val email: String?,
        val fullName: String,
        val role: String,
        val className: String?
    )

    data class ManagedSection(
        val id: String,
        val gradeName: String,
        val sectionCode: String,
        val isOpen: Boolean
    )

    data class TeacherAssignment(
        val subjectName: String,
        val className: String
    )

    /** Load only the authenticated teacher's assigned subject/class pairs. */
    suspend fun loadTeacherAssignments(session: Session): List<TeacherAssignment> = withContext(Dispatchers.IO) {
        val query = "select=subject_name,class_name&teacher_id=eq.${encode(session.userId)}&order=subject_name.asc,class_name.asc&limit=100"
        val rows = requestArray("/rest/v1/teacher_subjects?$query", session = session)
        (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            TeacherAssignment(
                subjectName = row.getString("subject_name"),
                className = row.getString("class_name")
            )
        }
    }

    data class SchoolItem(
        val id: String,
        val itemType: String,
        val title: String,
        val body: String,
        val subjectName: String?,
        val className: String?,
        val createdAt: String,
        val attachmentPath: String? = null
    )

    suspend fun signIn(email: String, password: String): Session = withContext(Dispatchers.IO) {
        requireConfigured()
        require(email.isNotBlank() && password.isNotEmpty()) { "أدخل البريد الإلكتروني وكلمة المرور." }
        val body = JSONObject().put("email", email.trim()).put("password", password)
        val result = request(
            path = "/auth/v1/token?grant_type=password",
            method = "POST",
            body = body,
            authenticated = false
        )
        val user = result.optJSONObject("user") ?: throw IOException("استجابة تسجيل الدخول غير مكتملة.")
        Session(
            userId = user.getString("id"),
            accessToken = result.getString("access_token"),
            refreshToken = result.optString("refresh_token")
        )
    }

    /** Restore a saved login using Supabase's rotating refresh token. */
    suspend fun refreshSession(refreshToken: String): Session = withContext(Dispatchers.IO) {
        requireConfigured()
        require(refreshToken.isNotBlank()) { "انتهت جلسة الدخول. سجّل الدخول من جديد." }
        val result = request(
            path = "/auth/v1/token?grant_type=refresh_token",
            method = "POST",
            body = JSONObject().put("refresh_token", refreshToken),
            authenticated = false
        )
        val user = result.optJSONObject("user") ?: throw IOException("تعذر استعادة جلسة الحساب.")
        Session(
            userId = user.getString("id"),
            accessToken = result.getString("access_token"),
            refreshToken = result.optString("refresh_token").ifBlank { refreshToken }
        )
    }

    suspend fun loadMyProfile(session: Session): Profile = withContext(Dispatchers.IO) {
        val query = "select=id,full_name,role,class_name,is_owner&id=eq.${encode(session.userId)}&limit=1"
        val rows = requestArray("/rest/v1/profiles?$query", session = session)
        if (rows.length() == 0) throw IOException("الحساب ما مرتبط بملف مدرسي. راجع إدارة المدرسة.")
        val row = rows.getJSONObject(0)
        Profile(
            id = row.getString("id"),
            fullName = row.getString("full_name"),
            role = row.getString("role"),
            className = row.optString("class_name").takeIf { it.isNotBlank() && it != "null" },
            isOwner = row.optBoolean("is_owner", false)
        )
    }

    /** The database only returns closed sections to the owner; ordinary users see open sections only. */
    suspend fun loadManagedSections(session: Session): List<ManagedSection> = withContext(Dispatchers.IO) {
        val rows = requestArray("/rest/v1/class_sections?select=id,grade_name,section_code,is_open&order=grade_name.asc,section_code.asc&limit=100", session = session)
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            ManagedSection(row.getString("id"), row.getString("grade_name"), row.getString("section_code"), row.optBoolean("is_open", false))
        }
    }

    suspend fun createManagedSection(session: Session, gradeName: String, sectionCode: String, isOpen: Boolean) = withContext(Dispatchers.IO) {
        require(gradeName.trim().length in 1..80) { "اسم الصف مطلوب." }
        require(sectionCode in setOf("A", "B", "C", "D", "E", "F")) { "الشعبة يجب أن تكون من A إلى F." }
        val payload = JSONObject().put("grade_name", gradeName.trim()).put("section_code", sectionCode).put("is_open", isOpen)
        requestRaw("/rest/v1/class_sections", "POST", payload, session)
        Unit
    }

    suspend fun setManagedSectionOpen(session: Session, sectionId: String, isOpen: Boolean) = withContext(Dispatchers.IO) {
        requestRaw("/rest/v1/class_sections?id=eq.${encode(sectionId)}", "PATCH", JSONObject().put("is_open", isOpen), session)
        Unit
    }

    data class RosterEntry(val id: String, val fullName: String, val gradeName: String, val sectionCode: String, val status: String)

    /** Owner-only bulk import from plain text: one student name per line. */
    suspend fun importStudentRosterText(session: Session, gradeName: String, sectionCode: String, text: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        require(gradeName.trim().length in 1..80) { "اسم الصف مطلوب." }
        val section = sectionCode.trim().uppercase()
        require(section in setOf("A","B","C","D","E","F")) { "اختر شعبة صحيحة." }
        val rawNames = text.lines().map { it.trim().replace(Regex("\\s+"), " ") }.filter { it.length >= 2 }.distinctBy { it.lowercase() }
        require(rawNames.isNotEmpty()) { "الصق اسمًا واحدًا على الأقل، كل اسم بسطر." }
        require(rawNames.size <= 1000) { "الحد الأقصى 1000 اسم في الدفعة الواحدة." }
        var inserted = 0
        var duplicates = 0
        for (name in rawNames) {
            val normalized = name.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
            val payload = JSONObject().put("full_name", name).put("normalized_name", normalized)
                .put("grade_name", gradeName.trim()).put("section_code", section)
                .put("status", "unmatched").put("created_by", session.userId)
            try {
                requestRaw("/rest/v1/student_roster", "POST", payload, session)
                inserted++
            } catch (e: Exception) { duplicates++ }
        }
        Pair(inserted, duplicates)
    }

    suspend fun loadStudentRoster(session: Session): List<RosterEntry> = withContext(Dispatchers.IO) {
        val rows = requestArray("/rest/v1/student_roster?select=id,full_name,grade_name,section_code,status&order=grade_name.asc,section_code.asc,full_name.asc&limit=2000", session = session)
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            RosterEntry(row.getString("id"), row.getString("full_name"), row.getString("grade_name"), row.getString("section_code"), row.getString("status"))
        }
    }

    /** Change the authenticated user's password through Supabase Auth. */
    suspend fun updatePassword(session: Session, newPassword: String) = withContext(Dispatchers.IO) {
        require(newPassword.length in 8..72) { "كلمة المرور يجب أن تكون بين 8 و72 محرفًا." }
        requestRaw("/auth/v1/user", "PUT", JSONObject().put("password", newPassword), session)
        Unit
    }

    /** Create a login account through the server-side owner-only Edge Function. No service-role key is shipped in Android. */
    suspend fun createManagedAccount(
        session: Session,
        fullName: String,
        email: String,
        password: String,
        role: String,
        className: String
    ) = withContext(Dispatchers.IO) {
        require(role in setOf(
            "student", "class_monitor", "parliament_member", "teacher", "class_supervisor",
            "assistant_principal", "principal", "educational_counselor", "ministry_education_member"
        )) { "نوع الحساب غير مسموح." }
        val payload = JSONObject()
            .put("full_name", fullName.trim())
            .put("email", email.trim())
            .put("password", password)
            .put("role", role)
            .put("class_name", className.trim().uppercase())
        requestRaw("/functions/v1/owner-create-account", "POST", payload, session)
        Unit
    }

    /** Visible to the owner through profiles RLS; other roles cannot enumerate the school. */
    suspend fun loadManagedProfiles(session: Session): List<ManagedProfile> = withContext(Dispatchers.IO) {
        val query = "select=id,email,full_name,role,class_name&order=full_name.asc&limit=500"
        val rows = requestArray("/rest/v1/profiles?$query", session = session)
        (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            ManagedProfile(
                id = row.getString("id"),
                email = row.optString("email").takeIf { it.isNotBlank() && it != "null" },
                fullName = row.getString("full_name"),
                role = row.getString("role"),
                className = row.optString("class_name").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    /** The database RPC independently verifies the owner flag and blocks app-side owner promotion. */
    suspend fun setManagedProfileRole(
        session: Session,
        targetUserId: String,
        role: String,
        className: String?
    ) = withContext(Dispatchers.IO) {
        require(role in setOf(
            "student", "class_monitor", "parliament_member", "teacher", "class_supervisor",
            "assistant_principal", "principal", "educational_counselor", "ministry_education_member"
        )) { "نوع الحساب غير مسموح." }
        val cleanClass = className?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
        require(cleanClass == null || cleanClass in setOf("A", "B", "C", "D", "E", "F")) { "الشعبة يجب أن تكون A أو B أو C أو D أو E أو F." }
        require(role != "student" || cleanClass != null) { "حدد شعبة الطالب من A إلى F." }
        val payload = JSONObject()
            .put("target_user_id", targetUserId)
            .put("role", role)
            .put("class_name", cleanClass ?: JSONObject.NULL)
        requestRaw("/functions/v1/owner-set-profile-role", "POST", payload, session)
        Unit
    }

    suspend fun loadPublishedItems(session: Session, className: String?): List<SchoolItem> = withContext(Dispatchers.IO) {
        val query = buildString {
            append("select=id,item_type,title,body,subject_name,class_name,created_at,attachment_path")
            append("&published=eq.true&order=created_at.desc&limit=100")
            if (!className.isNullOrBlank()) append("&or=(class_name.is.null,class_name.eq.${encode(className)})")
        }
        val rows = requestArray("/rest/v1/school_items?$query", session = session)
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            SchoolItem(
                id = row.getString("id"),
                itemType = row.getString("item_type"),
                title = row.getString("title"),
                body = row.optString("body"),
                subjectName = row.optString("subject_name").takeIf { it.isNotBlank() && it != "null" },
                className = row.optString("class_name").takeIf { it.isNotBlank() && it != "null" },
                createdAt = row.optString("created_at"),
                attachmentPath = row.optString("attachment_path").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    /** Create school content. PostgreSQL RLS is the final authority for role/subject permissions. */
    suspend fun createSchoolItem(
        session: Session,
        itemType: String,
        title: String,
        body: String,
        subjectName: String? = null,
        className: String? = null,
        published: Boolean = true,
        attachmentPath: String? = null
    ): SchoolItem = withContext(Dispatchers.IO) {
        require(itemType in setOf("homework", "preparation", "timetable", "announcement", "report")) {
            "نوع المنشور غير صالح."
        }
        require(title.isNotBlank() && title.length <= 160) { "العنوان مطلوب وبحد أقصى 160 حرفاً." }
        require(body.length <= 12000) { "تفاصيل المنشور تتجاوز الحد المسموح." }
        val payload = JSONObject()
            .put("item_type", itemType)
            .put("title", title.trim())
            .put("body", body.trim())
            .put("created_by", session.userId)
            .put("published", published)
        subjectName?.takeIf { it.isNotBlank() }?.let { payload.put("subject_name", it.trim()) }
        className?.takeIf { it.isNotBlank() }?.let { payload.put("class_name", it.trim()) }
        attachmentPath?.takeIf { it.isNotBlank() }?.let { payload.put("attachment_path", it) }
        val response = requestRaw(
            "/rest/v1/school_items?select=id,item_type,title,body,subject_name,class_name,created_at,attachment_path",
            "POST",
            payload,
            session,
            preferRepresentation = true
        )
        val rows = JSONArray(response)
        if (rows.length() == 0) throw IOException("تم إرسال المنشور لكن الخادم لم يرجع بياناته.")
        val row = rows.getJSONObject(0)
        SchoolItem(
            id = row.getString("id"),
            itemType = row.getString("item_type"),
            title = row.getString("title"),
            body = row.optString("body"),
            subjectName = row.optString("subject_name").takeIf { it.isNotBlank() && it != "null" },
            className = row.optString("class_name").takeIf { it.isNotBlank() && it != "null" },
            createdAt = row.optString("created_at"),
            attachmentPath = row.optString("attachment_path").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    suspend fun uploadSchoolFile(session: Session, fileName: String, mimeType: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        requireConfigured()
        require(bytes.isNotEmpty()) { "الملف فارغ." }
        require(bytes.size <= 20 * 1024 * 1024) { "حجم الملف يجب ألا يتجاوز 20 ميغابايت." }
        val safeName = fileName.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").take(100).ifBlank { "attachment" }
        val path = "${session.userId}/${UUID.randomUUID()}-$safeName"
        var connection: HttpURLConnection? = null
        try {
            connection = (URL("$baseUrl/storage/v1/object/ruven-school-files/${encodeStoragePath(path)}").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 30000
                doOutput = true
                setRequestProperty("apikey", publishableKey)
                setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                setRequestProperty("Content-Type", mimeType.ifBlank { "application/octet-stream" })
                setRequestProperty("x-upsert", "false")
                outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).optString("message").ifBlank { JSONObject(response).optString("error") } }.getOrDefault("")
                throw IOException(message.ifBlank { "تعذر رفع الملف إلى خادم المدرسة (HTTP $status)." })
            }
            path
        } finally { connection?.disconnect() }
    }

    suspend fun deleteSchoolFile(session: Session, path: String) = withContext(Dispatchers.IO) {
        requireConfigured()
        requestRaw("/storage/v1/object/ruven-school-files/${encodeStoragePath(path)}", "DELETE", null, session)
        Unit
    }

    suspend fun createSchoolFileSignedUrl(session: Session, path: String): String = withContext(Dispatchers.IO) {
        val result = request("/storage/v1/object/sign/ruven-school-files/${encodeStoragePath(path)}", "POST", JSONObject().put("expiresIn", 3600), session = session)
        val signed = result.optString("signedURL").ifBlank { throw IOException("تعذر إنشاء رابط الملف.") }
        if (signed.startsWith("http://") || signed.startsWith("https://")) signed else "$baseUrl/${signed.trimStart('/')}"
    }

    private fun encodeStoragePath(path: String): String = path.split("/").joinToString("/") { encode(it) }

    /** These mutations are restricted again by database RLS; only authorized administrators can change items. */
    suspend fun updateSchoolItem(
        session: Session,
        itemId: String,
        title: String,
        body: String,
        published: Boolean
    ) = withContext(Dispatchers.IO) {
        require(title.isNotBlank() && title.length <= 160) { "العنوان مطلوب وبحد أقصى 160 حرفاً." }
        require(body.length <= 12000) { "تفاصيل المنشور تتجاوز الحد المسموح." }
        val payload = JSONObject()
            .put("title", title.trim())
            .put("body", body.trim())
            .put("published", published)
        requestRaw(
            "/rest/v1/school_items?id=eq.${encode(itemId)}",
            "PATCH",
            payload,
            session,
            preferRepresentation = true
        )
        Unit
    }

    suspend fun deleteSchoolItem(session: Session, itemId: String) = withContext(Dispatchers.IO) {
        requestRaw("/rest/v1/school_items?id=eq.${encode(itemId)}", "DELETE", null, session)
        Unit
    }


    data class SchoolSeal(val id: String, val name: String, val title: String, val ownerId: String)
    data class SchoolDecision(
        val id: String, val type: String, val studentId: String, val sourceClass: String?,
        val targetClass: String?, val reason: String, val assignedMonitor: String?,
        val status: String, val createdBy: String, val sealId: String, val executionNote: String?,
        val createdAt: String
    )
    data class LeaveRequest(
        val id: String, val studentId: String, val className: String, val startsOn: String,
        val endsOn: String, val reason: String, val status: String, val reviewedBy: String?,
        val sealId: String?, val attendanceStatus: String, val createdAt: String
    )
    data class DailyReport(
        val id: String, val className: String, val reportDate: String, val events: String,
        val promises: String, val unresolved: String, val createdBy: String, val published: Boolean
    )
    data class UserNotification(
        val id: String, val type: String, val title: String, val body: String,
        val relatedId: String?, val readAt: String?, val createdAt: String
    )

    suspend fun loadSchoolSeals(session: Session): List<SchoolSeal> = withContext(Dispatchers.IO) {
        val rows = requestArray("/rest/v1/school_seals?select=id,seal_name,seal_title,owner_id,is_active&is_active=eq.true&order=created_at.desc&limit=100", session)
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let { SchoolSeal(it.getString("id"), it.getString("seal_name"), it.getString("seal_title"), it.getString("owner_id")) } }
    }

    suspend fun createSchoolSeal(session: Session, name: String, title: String) = withContext(Dispatchers.IO) {
        require(name.trim().length in 2..100 && title.trim().length in 2..100) { "اسم الختم وصفته مطلوبان." }
        requestRaw("/rest/v1/school_seals", "POST", JSONObject().put("owner_id", session.userId).put("seal_name", name.trim()).put("seal_title", title.trim()), session)
        Unit
    }

    suspend fun loadSchoolDecisions(session: Session): List<SchoolDecision> = withContext(Dispatchers.IO) {
        val rows = requestArray("/rest/v1/school_decisions?select=id,decision_type,student_id,source_class,target_class,reason,assigned_monitor,status,created_by,decision_seal_id,execution_note,created_at&order=created_at.desc&limit=200", session)
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let { r ->
            SchoolDecision(r.getString("id"),r.getString("decision_type"),r.getString("student_id"),r.optString("source_class").takeIf { it.isNotBlank() && it!="null" },r.optString("target_class").takeIf { it.isNotBlank() && it!="null" },r.getString("reason"),r.optString("assigned_monitor").takeIf { it.isNotBlank() && it!="null" },r.getString("status"),r.getString("created_by"),r.getString("decision_seal_id"),r.optString("execution_note").takeIf { it.isNotBlank() && it!="null" },r.getString("created_at"))
        } }
    }

    suspend fun createSchoolDecision(session: Session, type: String, studentId: String, sourceClass: String?, targetClass: String?, reason: String, monitorId: String?, sealId: String) = withContext(Dispatchers.IO) {
        require(type in setOf("transfer_student","remove_student","accept_request","other")) { "نوع القرار غير صالح." }
        require(reason.trim().length in 3..3000) { "اكتب سبب القرار." }
        val body = JSONObject().put("decision_type",type).put("student_id",studentId).put("reason",reason.trim()).put("created_by",session.userId).put("decision_seal_id",sealId)
        sourceClass?.takeIf { it.isNotBlank() }?.let { body.put("source_class",it.trim()) }
        targetClass?.takeIf { it.isNotBlank() }?.let { body.put("target_class",it.trim()) }
        monitorId?.takeIf { it.isNotBlank() }?.let { body.put("assigned_monitor",it) }
        requestRaw("/rest/v1/school_decisions", "POST", body, session)
        Unit
    }

    suspend fun updateSchoolDecision(session: Session, id: String, status: String, note: String = "") = withContext(Dispatchers.IO) {
        require(status in setOf("acknowledged","completed")) { "حالة التنفيذ غير صالحة." }
        val body = JSONObject().put("status",status).put("execution_note",note.trim())
        if (status=="completed") body.put("executed_by",session.userId).put("executed_at",java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()))
        requestRaw("/rest/v1/school_decisions?id=eq.${encode(id)}", "PATCH", body, session)
        Unit
    }

    suspend fun loadLeaveRequests(session: Session): List<LeaveRequest> = withContext(Dispatchers.IO) {
        val rows = requestArray("/rest/v1/leave_requests?select=id,student_id,class_name,starts_on,ends_on,reason,status,reviewed_by,decision_seal_id,attendance_status,created_at&order=created_at.desc&limit=200", session)
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let { r ->
            LeaveRequest(r.getString("id"),r.getString("student_id"),r.getString("class_name"),r.getString("starts_on"),r.getString("ends_on"),r.getString("reason"),r.getString("status"),r.optString("reviewed_by").takeIf { it.isNotBlank() && it!="null" },r.optString("decision_seal_id").takeIf { it.isNotBlank() && it!="null" },r.getString("attendance_status"),r.getString("created_at"))
        } }
    }

    suspend fun createLeaveRequest(session: Session, className: String, startsOn: String, endsOn: String, reason: String) = withContext(Dispatchers.IO) {
        require(className.isNotBlank() && startsOn.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && endsOn.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) && endsOn >= startsOn && reason.trim().length in 3..2000) { "تحقق من الشعبة والتواريخ وسبب الإجازة." }
        requestRaw("/rest/v1/leave_requests", "POST", JSONObject().put("student_id",session.userId).put("class_name",className.trim()).put("starts_on",startsOn).put("ends_on",endsOn).put("reason",reason.trim()), session)
        Unit
    }

    suspend fun reviewLeaveRequest(session: Session, id: String, approve: Boolean, sealId: String?) = withContext(Dispatchers.IO) {
        val body = JSONObject().put("status",if (approve) "approved" else "rejected").put("reviewed_by",session.userId).put("reviewed_at",java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date()))
        if (approve && !sealId.isNullOrBlank()) body.put("decision_seal_id",sealId)
        requestRaw("/rest/v1/leave_requests?id=eq.${encode(id)}", "PATCH", body, session)
        Unit
    }

    suspend fun markLeaveAttendance(session: Session, id: String, attendance: String) = withContext(Dispatchers.IO) {
        require(attendance in setOf("present","absent","excused")) { "حالة الحضور غير صالحة." }
        requestRaw("/rest/v1/leave_requests?id=eq.${encode(id)}", "PATCH", JSONObject().put("attendance_status",attendance).put("attendance_marked_by",session.userId).put("attendance_marked_at",java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date())), session)
        Unit
    }

    suspend fun loadDailyReports(session: Session, className: String?): List<DailyReport> = withContext(Dispatchers.IO) {
        val filter = className?.takeIf { it.isNotBlank() }?.let { "&class_name=eq.${encode(it)}" }.orEmpty()
        val rows = requestArray("/rest/v1/daily_class_reports?select=id,class_name,report_date,important_events,promises,unresolved_items,created_by,is_published&order=report_date.desc&limit=100$filter", session)
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let { r -> DailyReport(r.getString("id"),r.getString("class_name"),r.getString("report_date"),r.optString("important_events"),r.optString("promises"),r.optString("unresolved_items"),r.getString("created_by"),r.optBoolean("is_published")) } }
    }

    suspend fun createDailyReport(session: Session, className: String, reportDate: String, events: String, promises: String, unresolved: String, publish: Boolean) = withContext(Dispatchers.IO) {
        require(className.isNotBlank() && reportDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "تاريخ التقرير أو الشعبة غير صحيح." }
        val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date())
        val body = JSONObject().put("class_name",className.trim()).put("report_date",reportDate).put("important_events",events.trim()).put("promises",promises.trim()).put("unresolved_items",unresolved.trim()).put("created_by",session.userId).put("is_published",publish).put("published_at",if (publish) now else JSONObject.NULL)
        requestRaw("/rest/v1/daily_class_reports?on_conflict=class_name,report_date,created_by", "POST", body, session, preferRepresentation = true, upsert = true)
        Unit
    }

    suspend fun loadUserNotifications(session: Session): List<UserNotification> = withContext(Dispatchers.IO) {
        val rows = requestArray("/rest/v1/user_notifications?select=id,notification_type,title,body,related_id,read_at,created_at&order=created_at.desc&limit=100", session)
        (0 until rows.length()).map { i -> rows.getJSONObject(i).let { r -> UserNotification(r.getString("id"),r.getString("notification_type"),r.getString("title"),r.optString("body"),r.optString("related_id").takeIf { it.isNotBlank() && it!="null" },r.optString("read_at").takeIf { it.isNotBlank() && it!="null" },r.getString("created_at")) } }
    }

    suspend fun markNotificationRead(session: Session, id: String) = withContext(Dispatchers.IO) {
        requestRaw("/rest/v1/user_notifications?id=eq.${encode(id)}", "PATCH", JSONObject().put("read_at",java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.US).format(java.util.Date())), session)
        Unit
    }

    /** Append-only study event. RLS verifies the authenticated student owns the row. */
    suspend fun createStudentStudyRecord(
        session: Session,
        recordType: String,
        subjectName: String,
        chapterName: String? = null,
        lessonName: String? = null,
        durationSeconds: Long = 0L,
        lessonCompleted: Boolean = false,
        homeworkTitle: String? = null,
        homeworkStatus: String? = null,
        examKind: String? = null,
        pageNumber: Int? = null,
        metadata: JSONObject = JSONObject(),
        clientRecordId: String = UUID.randomUUID().toString()
    ) = withContext(Dispatchers.IO) {
        require(recordType in setOf("study_session", "lesson_progress", "homework_status", "exam_scope")) { "نوع سجل الدراسة غير صالح." }
        require(subjectName.isNotBlank() && subjectName.length <= 80) { "اسم المادة غير صالح." }
        require(durationSeconds in 0L..86400L) { "مدة الجلسة غير صالحة." }
        require(pageNumber == null || pageNumber >= 1) { "رقم الصفحة غير صالح." }
        require(runCatching { UUID.fromString(clientRecordId) }.isSuccess) { "معرّف السجل غير صالح." }
        val payload = JSONObject()
            .put("student_id", session.userId)
            .put("client_record_id", clientRecordId)
            .put("record_type", recordType)
            .put("subject_name", subjectName.trim())
            .put("duration_seconds", durationSeconds.toInt())
            .put("lesson_completed", lessonCompleted)
            .put("metadata", metadata)
        chapterName?.trim()?.takeIf { it.isNotBlank() }?.let { payload.put("chapter_name", it.take(160)) }
        lessonName?.trim()?.takeIf { it.isNotBlank() }?.let { payload.put("lesson_name", it.take(200)) }
        homeworkTitle?.trim()?.takeIf { it.isNotBlank() }?.let { payload.put("homework_title", it.take(200)) }
        homeworkStatus?.takeIf { it in setOf("كُتب", "اكتمل", "شبه منجز", "لم ينجز") }?.let { payload.put("homework_status", it) }
        examKind?.takeIf { it in setOf("شهري", "نصف السنة", "نهائي", "تجريبي", "أخرى") }?.let { payload.put("exam_kind", it) }
        pageNumber?.let { payload.put("page_number", it) }
        requestRaw(
            "/rest/v1/student_study_records?on_conflict=student_id,client_record_id",
            "POST", payload, session, ignoreDuplicates = true
        )
        Unit
    }

    data class StudentStudyRecord(
        val id: String,
        val studentId: String,
        val recordType: String,
        val subjectName: String,
        val chapterName: String?,
        val lessonName: String?,
        val durationSeconds: Int,
        val lessonCompleted: Boolean,
        val homeworkTitle: String?,
        val homeworkStatus: String?,
        val examKind: String?,
        val pageNumber: Int?,
        val metadata: JSONObject,
        val recordedAt: String
    )

    data class Study3DResource(
        val id: String,
        val subjectName: String,
        val chapterName: String?,
        val title: String,
        val description: String?,
        val licenseName: String,
        val attribution: String,
        val sourceUrl: String?,
        val fallback2DDescription: String,
        val fallback2DUrl: String?,
        val storagePath: String,
        val fileSizeBytes: Long,
        val status: String,
        val createdAt: String
    )

    data class TextbookCatalogEntry(
        val id: String,
        val gradeName: String,
        val className: String?,
        val subjectName: String,
        val title: String,
        val editionLabel: String,
        val publicationYear: Int?,
        val publisher: String?,
        val sourceName: String?,
        val sourceUrl: String?,
        val storagePath: String,
        val fileSizeBytes: Long,
        val status: String,
        val createdAt: String
    )

    /**
     * Uploads a PDF into the private bucket, then creates a pending catalogue row.
     * If the database insert fails, the just-uploaded object is removed.
     */
    suspend fun uploadTextbook(
        session: Session,
        gradeName: String,
        className: String?,
        subjectName: String,
        title: String,
        editionLabel: String,
        publicationYear: Int?,
        publisher: String?,
        sourceName: String?,
        sourceUrl: String?,
        pdfBytes: ByteArray
    ): TextbookCatalogEntry = withContext(Dispatchers.IO) {
        require(pdfBytes.size in 8..20_971_520) { "حجم ملف PDF يجب ألا يتجاوز 20 ميغابايت." }
        require(pdfBytes.copyOfRange(0, 5).toString(Charsets.US_ASCII) == "%PDF-") {
            "الملف المحدد ليس مستند PDF صالحاً."
        }
        require(gradeName.isNotBlank() && subjectName.isNotBlank() && title.isNotBlank() && editionLabel.isNotBlank()) {
            "أكمل الصف والمادة والعنوان والطبعة."
        }
        val cleanSourceUrl = sourceUrl?.trim()?.takeIf { it.isNotBlank() }
        require(cleanSourceUrl == null || cleanSourceUrl.startsWith("https://")) {
            "رابط المصدر يجب أن يبدأ بـ HTTPS."
        }
        val storagePath = "textbooks/${UUID.randomUUID()}.pdf"
        val encodedPath = storagePath.split("/").joinToString("/") { encode(it) }
        storageRequest("/storage/v1/object/ruven-school-files/$encodedPath", "POST", session, pdfBytes, "application/pdf")
        try {
            val row = JSONObject().apply {
                put("grade_name", gradeName.trim())
                put("class_name", className?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("subject_name", subjectName.trim())
                put("title", title.trim())
                put("edition_label", editionLabel.trim())
                put("publication_year", publicationYear ?: JSONObject.NULL)
                put("publisher", publisher?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("source_name", sourceName?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("source_url", cleanSourceUrl ?: JSONObject.NULL)
                put("storage_path", storagePath)
                put("mime_type", "application/pdf")
                put("file_size_bytes", pdfBytes.size)
                put("uploaded_by", session.userId)
                put("status", "pending")
            }
            val response = requestRaw("/rest/v1/textbook_catalog", "POST", row, session, preferRepresentation = true)
            val rows = JSONArray(response)
            require(rows.length() == 1) { "تم رفع الملف لكن تعذر تأكيد سجل الكتاب." }
            rows.getJSONObject(0).let(::parseTextbookCatalogEntry)
        } catch (error: Exception) {
            runCatching { storageRequest("/storage/v1/object/ruven-school-files/$encodedPath", "DELETE", session, null, null) }
            throw error
        }
    }

    suspend fun reviewTextbook(
        session: Session,
        textbookId: String,
        approve: Boolean
    ) = withContext(Dispatchers.IO) {
        require(textbookId.isNotBlank())
        val body = JSONObject().apply {
            put("status", if (approve) "approved" else "rejected")
            put("reviewed_by", session.userId)
            put("reviewed_at", java.time.Instant.now().toString())
        }
        requestRaw("/rest/v1/textbook_catalog?id=eq.${encode(textbookId)}", "PATCH", body, session)
        Unit
    }

    /** Fetches an approved PDF through authenticated Storage; RLS checks its catalogue state. */
    suspend fun downloadApprovedTextbook(session: Session, storagePath: String): ByteArray = withContext(Dispatchers.IO) {
        require(storagePath.startsWith("textbooks/") && !storagePath.contains("..")) {
            "مسار الكتاب غير صالح."
        }
        val encodedPath = storagePath.split("/").joinToString("/") { encode(it) }
        storageRequest("/storage/v1/object/authenticated/ruven-school-files/$encodedPath", "GET", session, null, null)
    }

    private fun storageRequest(
        path: String,
        method: String,
        session: Session,
        body: ByteArray?,
        contentType: String?
    ): ByteArray {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 20_000
                readTimeout = 45_000
                setRequestProperty("apikey", publishableKey)
                setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                if (contentType != null) setRequestProperty("Content-Type", contentType)
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body) }
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (status !in 200..299) {
                val raw = response.toString(Charsets.UTF_8)
                val message = runCatching {
                    JSONObject(raw).optString("message").ifBlank { JSONObject(raw).optString("error") }
                }.getOrDefault("")
                throw IOException(message.ifBlank { "تعذر التعامل مع ملف الكتاب (HTTP $status)." })
            }
            return response
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseTextbookCatalogEntry(row: JSONObject) = TextbookCatalogEntry(
        id = row.getString("id"),
        gradeName = row.getString("grade_name"),
        className = row.optString("class_name").takeIf { it.isNotBlank() && it != "null" },
        subjectName = row.getString("subject_name"),
        title = row.getString("title"),
        editionLabel = row.getString("edition_label"),
        publicationYear = if (row.isNull("publication_year")) null else row.optInt("publication_year"),
        publisher = row.optString("publisher").takeIf { it.isNotBlank() && it != "null" },
        sourceName = row.optString("source_name").takeIf { it.isNotBlank() && it != "null" },
        sourceUrl = row.optString("source_url").takeIf { it.startsWith("https://") },
        storagePath = row.getString("storage_path"),
        fileSizeBytes = row.optLong("file_size_bytes"),
        status = row.optString("status", "pending"),
        createdAt = row.optString("created_at")
    )

    /** Published catalogue entries are filtered by RLS to the student's class. */
    suspend fun loadTextbookCatalog(
        session: Session,
        subjectName: String? = null,
        gradeName: String? = null
    ): List<TextbookCatalogEntry> = withContext(Dispatchers.IO) {
        val filters = buildString {
            subjectName?.trim()?.takeIf { it.isNotBlank() }?.let { append("&subject_name=eq.${encode(it)}") }
            gradeName?.trim()?.takeIf { it.isNotBlank() }?.let { append("&grade_name=eq.${encode(it)}") }
        }
        val rows = requestArray(
            "/rest/v1/textbook_catalog?select=id,grade_name,class_name,subject_name,title,edition_label,publication_year,publisher,source_name,source_url,storage_path,file_size_bytes,status,created_at&order=grade_name.asc,subject_name.asc,updated_at.desc&limit=200$filters",
            session
        )
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            TextbookCatalogEntry(
                id = row.getString("id"),
                gradeName = row.getString("grade_name"),
                className = row.optString("class_name").takeIf { it.isNotBlank() && it != "null" },
                subjectName = row.getString("subject_name"),
                title = row.getString("title"),
                editionLabel = row.getString("edition_label"),
                publicationYear = if (row.isNull("publication_year")) null else row.optInt("publication_year"),
                publisher = row.optString("publisher").takeIf { it.isNotBlank() && it != "null" },
                sourceName = row.optString("source_name").takeIf { it.isNotBlank() && it != "null" },
                sourceUrl = row.optString("source_url").takeIf { it.startsWith("https://") },
                storagePath = row.getString("storage_path"),
                fileSizeBytes = row.optLong("file_size_bytes"),
                status = row.optString("status", "pending"),
                createdAt = row.optString("created_at")
            )
        }
    }

    private fun parseStudy3DResource(row: JSONObject) = Study3DResource(
        id = row.getString("id"),
        subjectName = row.getString("subject_name"),
        chapterName = row.optString("chapter_name").takeIf { it.isNotBlank() && it != "null" },
        title = row.getString("title"),
        description = row.optString("description").takeIf { it.isNotBlank() && it != "null" },
        licenseName = row.getString("license_name"),
        attribution = row.getString("attribution"),
        sourceUrl = row.optString("source_url").takeIf { it.startsWith("https://") },
        fallback2DDescription = row.getString("fallback_2d_description"),
        fallback2DUrl = row.optString("fallback_2d_url").takeIf { it.startsWith("https://") },
        storagePath = row.getString("storage_path"),
        fileSizeBytes = row.optLong("file_size_bytes"),
        status = row.optString("status", "pending"),
        createdAt = row.optString("created_at")
    )

    suspend fun loadStudy3DResources(
        session: Session,
        subjectName: String? = null
    ): List<Study3DResource> = withContext(Dispatchers.IO) {
        val filter = subjectName?.trim()?.takeIf { it.isNotBlank() }
            ?.let { "&subject_name=eq.${encode(it)}" }.orEmpty()
        val rows = requestArray(
            "/rest/v1/study_3d_resources?select=id,subject_name,chapter_name,title,description,license_name,attribution,source_url,fallback_2d_description,fallback_2d_url,storage_path,file_size_bytes,status,created_at&order=subject_name.asc,updated_at.desc&limit=100$filter",
            session
        )
        (0 until rows.length()).map { parseStudy3DResource(rows.getJSONObject(it)) }
    }

    suspend fun uploadStudy3DResource(
        session: Session,
        subjectName: String,
        chapterName: String?,
        title: String,
        description: String?,
        licenseName: String,
        attribution: String,
        sourceUrl: String?,
        fallback2DDescription: String,
        fallback2DUrl: String?,
        glbBytes: ByteArray
    ): Study3DResource = withContext(Dispatchers.IO) {
        require(glbBytes.size in 12..26_214_400) { "حجم ملف GLB يجب ألا يتجاوز 25 ميغابايت." }
        val declaredLength = iq.ruven.app.glbDeclaredLength(glbBytes.copyOfRange(0, 12))
            ?: throw IllegalArgumentException("ملف GLB غير صالح أو لا يستخدم glTF 2.0.")
        require(declaredLength == glbBytes.size.toLong()) { "طول ملف GLB لا يطابق الترويسة." }
        require(subjectName.isNotBlank() && title.isNotBlank() && licenseName.isNotBlank() &&
            attribution.isNotBlank() && fallback2DDescription.isNotBlank()) {
            "أكمل المادة والعنوان والترخيص ونسبة المصدر والبديل ثنائي الأبعاد."
        }
        val cleanSourceUrl = sourceUrl?.trim()?.takeIf { it.isNotBlank() }
        val cleanFallbackUrl = fallback2DUrl?.trim()?.takeIf { it.isNotBlank() }
        require(cleanSourceUrl == null || cleanSourceUrl.startsWith("https://")) { "رابط المصدر يجب أن يبدأ بـ HTTPS." }
        require(cleanFallbackUrl == null || cleanFallbackUrl.startsWith("https://")) { "رابط البديل يجب أن يبدأ بـ HTTPS." }

        val storagePath = "study-models/${UUID.randomUUID()}.glb"
        val encodedPath = storagePath.split("/").joinToString("/") { encode(it) }
        storageRequest("/storage/v1/object/ruven-school-files/$encodedPath", "POST", session, glbBytes, "model/gltf-binary")
        try {
            val row = JSONObject().apply {
                put("subject_name", subjectName.trim())
                put("chapter_name", chapterName?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("title", title.trim())
                put("description", description?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
                put("license_name", licenseName.trim())
                put("attribution", attribution.trim())
                put("source_url", cleanSourceUrl ?: JSONObject.NULL)
                put("fallback_2d_description", fallback2DDescription.trim())
                put("fallback_2d_url", cleanFallbackUrl ?: JSONObject.NULL)
                put("storage_path", storagePath)
                put("mime_type", "model/gltf-binary")
                put("file_size_bytes", glbBytes.size)
                put("uploaded_by", session.userId)
                put("status", "pending")
            }
            val response = requestRaw("/rest/v1/study_3d_resources", "POST", row, session, preferRepresentation = true)
            val rows = JSONArray(response)
            require(rows.length() == 1) { "تم رفع المجسم لكن تعذر تأكيد سجل المورد." }
            parseStudy3DResource(rows.getJSONObject(0))
        } catch (failure: Exception) {
            runCatching { storageRequest("/storage/v1/object/ruven-school-files/$encodedPath", "DELETE", session, null, null) }
            throw failure
        }
    }

    suspend fun reviewStudy3DResource(
        session: Session,
        resourceId: String,
        approve: Boolean
    ) = withContext(Dispatchers.IO) {
        require(resourceId.isNotBlank())
        val body = JSONObject().apply {
            put("status", if (approve) "approved" else "rejected")
            put("reviewed_by", session.userId)
            put("reviewed_at", java.time.Instant.now().toString())
        }
        requestRaw("/rest/v1/study_3d_resources?id=eq.${encode(resourceId)}", "PATCH", body, session)
        Unit
    }

    suspend fun downloadApprovedStudy3DResource(session: Session, storagePath: String): ByteArray =
        withContext(Dispatchers.IO) {
            require(storagePath.startsWith("study-models/") && !storagePath.contains("..")) {
                "مسار المجسم غير صالح."
            }
            val encodedPath = storagePath.split("/").joinToString("/") { encode(it) }
            storageRequest("/storage/v1/object/authenticated/ruven-school-files/$encodedPath", "GET", session, null, null)
        }

    /** Admins can see all records through RLS; students can only retrieve their own. */
    suspend fun loadStudentStudyRecords(session: Session, studentId: String? = null): List<StudentStudyRecord> = withContext(Dispatchers.IO) {
        val filter = studentId?.takeIf { it.isNotBlank() }?.let { "&student_id=eq.${encode(it)}" }.orEmpty()
        val rows = requestArray("/rest/v1/student_study_records?select=id,student_id,record_type,subject_name,chapter_name,lesson_name,duration_seconds,lesson_completed,homework_title,homework_status,exam_kind,page_number,metadata,recorded_at&order=recorded_at.desc&limit=500$filter", session)
        (0 until rows.length()).map { i ->
            val row = rows.getJSONObject(i)
            StudentStudyRecord(
                id = row.getString("id"),
                studentId = row.getString("student_id"),
                recordType = row.getString("record_type"),
                subjectName = row.getString("subject_name"),
                chapterName = row.optString("chapter_name").takeIf { it.isNotBlank() && it != "null" },
                lessonName = row.optString("lesson_name").takeIf { it.isNotBlank() && it != "null" },
                durationSeconds = row.optInt("duration_seconds"),
                lessonCompleted = row.optBoolean("lesson_completed"),
                homeworkTitle = row.optString("homework_title").takeIf { it.isNotBlank() && it != "null" },
                homeworkStatus = row.optString("homework_status").takeIf { it.isNotBlank() && it != "null" },
                examKind = row.optString("exam_kind").takeIf { it.isNotBlank() && it != "null" },
                pageNumber = if (row.isNull("page_number")) null else row.optInt("page_number"),
                metadata = row.optJSONObject("metadata") ?: JSONObject(),
                recordedAt = row.optString("recorded_at")
            )
        }
    }

    suspend fun signOut(session: Session) = withContext(Dispatchers.IO) {
        if (baseUrl.isBlank() || publishableKey.isBlank()) return@withContext
        runCatching {
            request("/auth/v1/logout", method = "POST", session = session, body = JSONObject())
        }
        Unit
    }

    private fun requestArray(path: String, session: Session): JSONArray {
        val text = requestRaw(path, "GET", null, session)
        return JSONArray(text)
    }

    private fun request(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        authenticated: Boolean = true,
        session: Session? = null
    ): JSONObject {
        val raw = requestRaw(path, method, body, if (authenticated) session else null)
        return if (raw.isBlank()) JSONObject() else JSONObject(raw)
    }

    private fun requestRaw(
        path: String,
        method: String,
        body: JSONObject?,
        session: Session?,
        preferRepresentation: Boolean = false,
        upsert: Boolean = false,
        ignoreDuplicates: Boolean = false
    ): String {
        requireConfigured()
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 12_000
                readTimeout = 12_000
                setRequestProperty("apikey", publishableKey)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
                if (ignoreDuplicates) setRequestProperty("Prefer", "resolution=ignore-duplicates,return=minimal")
                else if (upsert) setRequestProperty("Prefer", "resolution=merge-duplicates,return=representation")
                else if (preferRepresentation) setRequestProperty("Prefer", "return=representation")
                if (session != null) setRequestProperty("Authorization", "Bearer ${session.accessToken}")
                if (body != null) {
                    doOutput = true
                    outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching {
                    val error = JSONObject(response)
                    error.optString("error")
                        .ifBlank { error.optString("message") }
                        .ifBlank { error.optString("msg") }
                        .ifBlank { error.optString("error_description") }
                }.getOrDefault("")
                throw IOException(message.ifBlank { "تعذر الاتصال بالخادم (HTTP $status)." })
            }
            return response
        } finally {
            connection?.disconnect()
        }
    }

    private fun requireConfigured() {
        if (baseUrl.isBlank() || publishableKey.isBlank()) {
            throw IOException("إعدادات Supabase غير مضافة إلى إعدادات بناء التطبيق.")
        }
        if (!baseUrl.startsWith("https://")) throw IOException("عنوان Supabase يجب أن يبدأ بـ HTTPS.")
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
