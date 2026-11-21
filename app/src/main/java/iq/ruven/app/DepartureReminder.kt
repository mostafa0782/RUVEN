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

private const val DEPARTURE_CHANNEL_ID = "ruven_departure_reminder_v1"
private const val DEPARTURE_WORK_NAME = "ruven_departure_reminder"

internal fun scheduleDepartureReminder(context: Context, minutes: Int): Boolean {
    if (minutes !in 1..720) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    val request = OneTimeWorkRequestBuilder<DepartureReminderWorker>()
        .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
        .setInputData(Data.Builder().putInt("minutes", minutes).build())
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(DEPARTURE_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    return true
}

internal class DepartureReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val minutes = inputData.getInt("minutes", 0)
        if (minutes !in 1..720) return Result.failure()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(DEPARTURE_CHANNEL_ID, "تذكير المغادرة", NotificationManager.IMPORTANCE_DEFAULT))
        }
        return try {
            NotificationManagerCompat.from(applicationContext).notify(
                73105,
                NotificationCompat.Builder(applicationContext, DEPARTURE_CHANNEL_ID)
                    .setSmallIcon(applicationContext.applicationInfo.icon)
                    .setContentTitle("موعد الاستعداد للمغادرة")
                    .setContentText("حان وقت الاستعداد حسب التذكير الذي ضبطته.")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("مرّت $minutes دقيقة منذ ضبط تذكير المغادرة. جهّز أغراضك وتحقق من وسيلة النقل والوقت بنفسك."))
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
