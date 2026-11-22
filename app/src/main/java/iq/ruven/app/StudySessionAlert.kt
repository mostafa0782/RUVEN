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
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

private const val STUDY_ALERT_CHANNEL = "ruven_study_session_alerts_v1"

internal fun scheduleStudySessionAlert(
    context: Context,
    userId: String,
    delaySeconds: Long,
    title: String,
    message: String
): Boolean {
    if (delaySeconds !in 1L..(24L * 60L * 60L)) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false

    val request = OneTimeWorkRequestBuilder<StudySessionAlertWorker>()
        .setInitialDelay(delaySeconds, TimeUnit.SECONDS)
        .setInputData(
            Data.Builder()
                .putString("title", title.take(100))
                .putString("message", message.take(500))
                .build()
        )
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "ruven_study_alert_$userId",
        ExistingWorkPolicy.REPLACE,
        request
    )
    return true
}

internal fun cancelStudySessionAlert(context: Context, userId: String) {
    WorkManager.getInstance(context).cancelUniqueWork("ruven_study_alert_$userId")
}

internal class StudySessionAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val title = inputData.getString("title")?.takeIf { it.isNotBlank() } ?: return Result.failure()
        val message = inputData.getString("message").orEmpty()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(STUDY_ALERT_CHANNEL, "جلسات الدراسة", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "تنبيهات الانتقال بين التركيز والاستراحة وإكمال هدف الدراسة"
                }
            )
        }
        return try {
            NotificationManagerCompat.from(applicationContext).notify(
                73105,
                NotificationCompat.Builder(applicationContext, STUDY_ALERT_CHANNEL)
                    .setSmallIcon(applicationContext.applicationInfo.icon)
                    .setContentTitle(title)
                    .setContentText(message)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                    .build()
            )
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        }
    }
}
