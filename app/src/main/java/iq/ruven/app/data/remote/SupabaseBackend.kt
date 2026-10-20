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

    private fun requestRaw(path: String, method: String, body: JSONObject?, session: Session?): String {
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
