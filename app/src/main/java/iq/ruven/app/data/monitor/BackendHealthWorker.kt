package iq.ruven.app.data.monitor

import android.content.Context
import iq.ruven.app.RuvenNotifications
import iq.ruven.app.StudyRecordOutbox
import iq.ruven.app.data.remote.SupabaseBackend
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import iq.ruven.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Low-frequency backend availability check used for diagnostics.
 * It performs a read-only request and does not create synthetic database records.
 */
class BackendHealthWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
        val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
        if (baseUrl.isBlank() || publishableKey.isBlank()) return@withContext Result.success()

        // Refresh the signed-in session and sync workflow alerts in the background.
        // A workflow sync failure must not prevent the independent health check.
        val workflowSyncComplete = runCatching { syncWorkflowNotifications() }
            .onFailure { Log.d(TAG, "Workflow/study sync will retry: ${it.message}") }
            .getOrDefault(false)

        var connection: HttpURLConnection? = null
        try {
            val url = URL("$baseUrl/rest/v1/profiles?select=id&limit=1")
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("apikey", publishableKey)
                setRequestProperty("Accept", "application/json")
            }
            val status = connection.responseCode
            when {
                status in 200..299 -> {
                    Log.d(TAG, "Backend health check succeeded (HTTP $status)")
                    if (workflowSyncComplete) Result.success() else {
                        Log.d(TAG, "Study outbox still has pending records; scheduling retry")
                        Result.retry()
                    }
                }
                status >= 500 || status == 429 -> {
                    Log.w(TAG, "Backend temporarily unavailable (HTTP $status)")
                    Result.retry()
                }
                else -> {
                    // Configuration/schema errors should not trigger repeated requests.
                    Log.w(TAG, "Backend health check needs attention (HTTP $status)")
                    Result.success()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Backend health check failed; will retry according to WorkManager backoff", e)
            Result.retry()
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun syncWorkflowNotifications(): Boolean {
        val prefs = applicationContext.getSharedPreferences("ruven_auth", Context.MODE_PRIVATE)
        val refreshToken = prefs.getString("refresh_token", null)?.takeIf { it.isNotBlank() } ?: return true
        val backend = SupabaseBackend()
        val session = backend.refreshSession(refreshToken)
        prefs.edit().putString("refresh_token", session.refreshToken).apply()
        val profile = backend.loadMyProfile(session)
        val items = backend.loadUserNotifications(session)
        RuvenNotifications.processWorkflowSync(applicationContext, profile.id, items)

        val studySync = StudyRecordOutbox.syncPending(applicationContext, backend, session)
        if (studySync.pendingCount > 0) {
            Log.d(TAG, "Study outbox pending records: ${studySync.pendingCount}")
            return false
        }
        return true
    }

    companion object {
        const val UNIQUE_WORK_NAME = "ruven_backend_health_check"
        private const val TAG = "RuvenBackendHealth"
    }
}
