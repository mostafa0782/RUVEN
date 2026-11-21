package iq.ruven.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingWorkPolicy
import java.util.concurrent.TimeUnit

private const val REVIEW_CHANNEL_ID = "ruven_spaced_review"

internal fun scheduleSpacedReviewReminders(context: Context, lesson: String) {
    val cleanLesson = lesson.trim().take(160)
    if (cleanLesson.isBlank()) return
    listOf(1L, 3L, 7L).forEach { day ->
        val data = Data.Builder().putString("lesson", cleanLesson).putLong("day", day).build()
        val request = OneTimeWorkRequestBuilder<SpacedReviewWorker>().setInitialDelay(day, TimeUnit.DAYS).setInputData(data).build()
        WorkManager.getInstance(context).enqueueUniqueWork("ruven_review_${cleanLesson.hashCode()}_$day", ExistingWorkPolicy.REPLACE, request)
    }
}

internal class SpacedReviewWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val lesson = inputData.getString("lesson")?.take(160).orEmpty()
        val day = inputData.getLong("day", 1L)
        if (lesson.isBlank()) return Result.failure()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(NotificationChannel(REVIEW_CHANNEL_ID, "Study review reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Optional reminders to review saved lessons" })
        val body = "راجع درس: $lesson"
        return try {
            NotificationManagerCompat.from(applicationContext).notify((lesson.hashCode() xor day.toInt()).and(Int.MAX_VALUE), NotificationCompat.Builder(applicationContext, REVIEW_CHANNEL_ID).setSmallIcon(applicationContext.applicationInfo.icon).setContentTitle("RUVEN · وقت المراجعة").setContentText(body).setStyle(NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).build())
            Result.success()
        } catch (_: SecurityException) { Result.success() }
    }
}
