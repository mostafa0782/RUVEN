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

    data class SchoolItem(
        val id: String,
        val itemType: String,
        val title: String,
        val body: String,
        val subjectName: String?,
        val className: String?,
        val createdAt: String
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
        val payload = JSONObject()
            .put("target_user_id", targetUserId)
            .put("new_role", role)
            .put("new_class_name", className?.trim()?.takeIf { it.isNotBlank() } ?: JSONObject.NULL)
        requestRaw("/rest/v1/rpc/owner_set_profile_role", "POST", payload, session)
        Unit
    }

    suspend fun loadPublishedItems(session: Session, className: String?): List<SchoolItem> = withContext(Dispatchers.IO) {
        val query = buildString {
            append("select=id,item_type,title,body,subject_name,class_name,created_at")
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
                createdAt = row.optString("created_at")
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
        published: Boolean = true
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
        val response = requestRaw(
            "/rest/v1/school_items?select=id,item_type,title,body,subject_name,class_name,created_at",
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
            createdAt = row.optString("created_at")
        )
    }

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
        preferRepresentation: Boolean = false
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
                if (preferRepresentation) setRequestProperty("Prefer", "return=representation")
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
                val message = runCatching { JSONObject(response).optString("message").ifBlank { JSONObject(response).optString("msg") } }.getOrDefault("")
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
