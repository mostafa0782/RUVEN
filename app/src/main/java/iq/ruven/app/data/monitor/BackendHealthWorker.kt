package iq.ruven.app.data.monitor

import android.content.Context
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
                    Result.success()
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

    companion object {
        const val UNIQUE_WORK_NAME = "ruven_backend_health_check"
        private const val TAG = "RuvenBackendHealth"
    }
}
