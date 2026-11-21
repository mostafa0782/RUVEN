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

private const val BREAK_CHANNEL_ID = "ruven_break_timer_v1"
private const val BREAK_WORK_NAME = "ruven_student_break_timer"

internal fun scheduleStudentBreak(context: Context, minutes: Int): Boolean {
    if (minutes !in 1..180) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return false

    val request = OneTimeWorkRequestBuilder<BreakReminderWorker>()
        .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
        .setInputData(Data.Builder().putInt("minutes", minutes).build())
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(BREAK_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    return true
}

internal fun cancelStudentBreak(context: Context) {
    WorkManager.getInstance(context).cancelUniqueWork(BREAK_WORK_NAME)
}

internal class BreakReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(BREAK_CHANNEL_ID, "استراحات RUVEN", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "تذكير اختياري بانتهاء وقت الاستراحة"
                }
            )
        }
        val minutes = inputData.getInt("minutes", 0)
        if (minutes !in 1..180) return Result.failure()
        return try {
            NotificationManagerCompat.from(applicationContext).notify(
                73104,
                NotificationCompat.Builder(applicationContext, BREAK_CHANNEL_ID)
                    .setSmallIcon(applicationContext.applicationInfo.icon)
                    .setContentTitle("انتهت الاستراحة")
                    .setContentText("حان وقت العودة إلى دراستك — حسب المؤقت الذي ضبطته.")
                    .setStyle(NotificationCompat.BigTextStyle().bigText("انتهت استراحتك المحددة بـ $minutes دقيقة. يمكنك تمديدها أو العودة للدراسة."))
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
