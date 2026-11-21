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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Duration
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.TimeUnit

internal data class ExamDeadline(val subject: String, val date: LocalDate)

internal fun parseExamDeadline(value: String): ExamDeadline? {
    val parts = value.split("|", limit = 2)
    if (parts.size != 2) return null
    val subject = parts[0].trim().take(100)
    if (subject.isBlank()) return null
    val dateText = parts[1].trim()
    if (!dateText.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) return null
    val date = try {
        LocalDate.parse(dateText, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: DateTimeParseException) {
        return null
    }
    if (date.isBefore(LocalDate.now())) return null
    return ExamDeadline(subject, date)
}

internal fun scheduleExamDeadlineReminder(context: Context, deadline: ExamDeadline) {
    val now = LocalDateTime.now()
    val reminderAt = deadline.date.minusDays(1).atTime(LocalTime.of(9, 0))
    val delayMillis = Duration.between(now, reminderAt).toMillis().coerceAtLeast(0L)
    val data = Data.Builder()
        .putString("subject", deadline.subject)
        .putString("date", deadline.date.toString())
        .build()
    val request = OneTimeWorkRequestBuilder<ExamDeadlineWorker>()
        .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
        .setInputData(data)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(
        "ruven_exam_${deadline.subject.hashCode()}_${deadline.date}",
        ExistingWorkPolicy.REPLACE,
        request
    )
}

internal class ExamDeadlineWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val subject = inputData.getString("subject")?.take(100).orEmpty()
        val date = inputData.getString("date").orEmpty()
        if (subject.isBlank() || date.isBlank()) return Result.failure()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "مواعيد الامتحانات", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "تذكيرات محلية بمواعيد الامتحانات والتسليم في RUVEN"
                }
            )
        }
        val body = "موعد $subject بتاريخ $date. راجع استعدادك والمواد المطلوبة."
        return try {
            NotificationManagerCompat.from(applicationContext).notify(
                (subject.hashCode() xor date.hashCode()).and(Int.MAX_VALUE),
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                    .setSmallIcon(applicationContext.applicationInfo.icon)
                    .setContentTitle("RUVEN · تذكير بموعد دراسي")
                    .setContentText(body)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .build()
            )
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        }
    }

    private companion object {
        const val CHANNEL_ID = "ruven_exam_deadlines"
    }
}
