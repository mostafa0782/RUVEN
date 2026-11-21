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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

private const val EYE_BREAK_WORK = "ruven_eye_break_reminder"
private const val EYE_BREAK_CHANNEL = "ruven_eye_breaks"

internal fun scheduleEyeBreakReminder(context: Context, intervalMinutes: Int): Boolean {
    if (intervalMinutes !in 15..180) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false
    val request = PeriodicWorkRequestBuilder<EyeBreakWorker>(intervalMinutes.toLong(), TimeUnit.MINUTES).build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(EYE_BREAK_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    return true
}

internal fun cancelEyeBreakReminder(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(EYE_BREAK_WORK)
}

internal class EyeBreakWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(EYE_BREAK_CHANNEL, "استراحات العين", NotificationManager.IMPORTANCE_LOW))
        }
        return try {
            NotificationManagerCompat.from(applicationContext).notify(
                73106,
                NotificationCompat.Builder(applicationContext, EYE_BREAK_CHANNEL)
                    .setSmallIcon(applicationContext.applicationInfo.icon)
                    .setContentTitle("استراحة قصيرة للعين")
                    .setContentText("حوّل نظرك عن الشاشة قليلًا، وارجع عندما تكون مستعدًا.")
                    .setPriority(NotificationCompat.PRIORITY_LOW)
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
