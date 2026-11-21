package iq.ruven.app

import android.content.Context
import iq.ruven.app.data.remote.SupabaseBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Durable, account-scoped queue for study records.
 *
 * Records are persisted before the network request. A stable client_record_id
 * plus the database unique constraint makes a retry safe if the server accepted
 * a request but the app lost the response before removing it from this queue.
 */
data class StudyRecordDraft(
    val recordType: String,
    val subjectName: String,
    val chapterName: String? = null,
    val lessonName: String? = null,
    val durationSeconds: Long = 0L,
    val lessonCompleted: Boolean = false,
    val homeworkTitle: String? = null,
    val homeworkStatus: String? = null,
    val examKind: String? = null,
    val pageNumber: Int? = null,
    val clientRecordId: String = UUID.randomUUID().toString()
) {
    init {
        require(recordType in setOf("study_session", "lesson_progress", "homework_status", "exam_scope"))
        require(subjectName.isNotBlank() && subjectName.length <= 80)
        require(durationSeconds in 0L..86400L)
        require(pageNumber == null || pageNumber >= 1)
        UUID.fromString(clientRecordId)
    }

    internal fun toJson(userId: String) = JSONObject()
        .put("user_id", userId)
        .put("client_record_id", clientRecordId)
        .put("record_type", recordType)
        .put("subject_name", subjectName)
        .put("chapter_name", chapterName ?: JSONObject.NULL)
        .put("lesson_name", lessonName ?: JSONObject.NULL)
        .put("duration_seconds", durationSeconds)
        .put("lesson_completed", lessonCompleted)
        .put("homework_title", homeworkTitle ?: JSONObject.NULL)
        .put("homework_status", homeworkStatus ?: JSONObject.NULL)
        .put("exam_kind", examKind ?: JSONObject.NULL)
        .put("page_number", pageNumber ?: JSONObject.NULL)

    companion object {
        internal fun fromJson(row: JSONObject): StudyRecordDraft = StudyRecordDraft(
            recordType = row.getString("record_type"),
            subjectName = row.getString("subject_name"),
            chapterName = row.optString("chapter_name").takeUnless { it.isBlank() || it == "null" },
            lessonName = row.optString("lesson_name").takeUnless { it.isBlank() || it == "null" },
            durationSeconds = row.optLong("duration_seconds"),
            lessonCompleted = row.optBoolean("lesson_completed"),
            homeworkTitle = row.optString("homework_title").takeUnless { it.isBlank() || it == "null" },
            homeworkStatus = row.optString("homework_status").takeUnless { it.isBlank() || it == "null" },
            examKind = row.optString("exam_kind").takeUnless { it.isBlank() || it == "null" },
            pageNumber = if (row.isNull("page_number")) null else row.optInt("page_number"),
            clientRecordId = row.getString("client_record_id")
        )
    }
}

data class StudyOutboxSyncResult(
    val syncedCount: Int,
    val pendingCount: Int,
    val errorMessage: String? = null
)

object StudyRecordOutbox {
    private const val PREFS = "ruven_study_outbox_v1"
    private const val KEY_RECORDS = "records"
    private const val MAX_PENDING = 1000

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun readAll(context: Context): JSONArray =
        runCatching { JSONArray(preferences(context).getString(KEY_RECORDS, "[]")) }
            .getOrElse { JSONArray() }

    private fun persist(context: Context, records: JSONArray) {
        check(preferences(context).edit().putString(KEY_RECORDS, records.toString()).commit()) {
            "تعذر حفظ سجل الدراسة محلياً."
        }
    }

    private fun enqueue(context: Context, userId: String, draft: StudyRecordDraft) {
        synchronized(this) {
            val records = readAll(context)
            val alreadyQueued = (0 until records.length()).any { index ->
                records.optJSONObject(index)?.optString("client_record_id") == draft.clientRecordId
            }
            if (alreadyQueued) return
            require(records.length() < MAX_PENDING) { "قائمة المزامنة ممتلئة؛ اتصل بالإنترنت لمزامنتها أولاً." }
            records.put(draft.toJson(userId))
            persist(context, records)
        }
    }

    private fun pendingForUser(context: Context, userId: String): List<StudyRecordDraft> =
        synchronized(this) {
            val records = readAll(context)
            (0 until records.length()).mapNotNull { index ->
                val row = records.optJSONObject(index) ?: return@mapNotNull null
                if (row.optString("user_id") != userId) return@mapNotNull null
                runCatching { StudyRecordDraft.fromJson(row) }.getOrNull()
            }
        }

    private fun remove(context: Context, clientRecordId: String) {
        synchronized(this) {
            val current = readAll(context)
            val retained = JSONArray()
            for (index in 0 until current.length()) {
                val row = current.optJSONObject(index) ?: continue
                if (row.optString("client_record_id") != clientRecordId) retained.put(row)
            }
            persist(context, retained)
        }
    }

    suspend fun enqueueAndSync(
        context: Context,
        backend: SupabaseBackend,
        session: SupabaseBackend.Session,
        draft: StudyRecordDraft
    ): StudyOutboxSyncResult = withContext(Dispatchers.IO) {
        enqueue(context, session.userId, draft)
        syncPendingInternal(context, backend, session)
    }

    suspend fun syncPending(
        context: Context,
        backend: SupabaseBackend,
        session: SupabaseBackend.Session
    ): StudyOutboxSyncResult = withContext(Dispatchers.IO) {
        syncPendingInternal(context, backend, session)
    }

    private suspend fun syncPendingInternal(
        context: Context,
        backend: SupabaseBackend,
        session: SupabaseBackend.Session
    ): StudyOutboxSyncResult {
        var synced = 0
        var error: String? = null
        for (draft in pendingForUser(context, session.userId)) {
            try {
                backend.createStudentStudyRecord(
                    session = session,
                    recordType = draft.recordType,
                    subjectName = draft.subjectName,
                    chapterName = draft.chapterName,
                    lessonName = draft.lessonName,
                    durationSeconds = draft.durationSeconds,
                    lessonCompleted = draft.lessonCompleted,
                    homeworkTitle = draft.homeworkTitle,
                    homeworkStatus = draft.homeworkStatus,
                    examKind = draft.examKind,
                    pageNumber = draft.pageNumber,
                    clientRecordId = draft.clientRecordId
                )
                remove(context, draft.clientRecordId)
                synced++
            } catch (failure: Exception) {
                error = failure.message ?: "تعذرت المزامنة."
                break
            }
        }
        return StudyOutboxSyncResult(
            syncedCount = synced,
            pendingCount = pendingForUser(context, session.userId).size,
            errorMessage = error
        )
    }

    suspend fun pendingCount(context: Context, userId: String): Int =
        withContext(Dispatchers.IO) { pendingForUser(context, userId).size }
}
