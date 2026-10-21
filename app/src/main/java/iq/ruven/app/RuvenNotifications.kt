package iq.ruven.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import iq.ruven.app.data.remote.SupabaseBackend

/**
 * RUVEN's native notification layer.
 *
 * The first successful sync establishes a baseline silently. Later syncs notify only
 * about genuinely new server items, preventing notification floods and duplicates.
 */
object RuvenNotifications {
    private const val CHANNEL_ID = "ruven_school_updates_v1"
    private const val PREFS_NAME = "ruven_notification_state"
    private val BLUE = 0xFF2867D8.toInt()

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "تحديثات المدرسة",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "واجبات وتحاضير وإعلانات وتغييرات الجدول في RUVEN"
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * @return number of notifications posted. The first sync only records current IDs.
     */
    fun processSync(
        context: Context,
        profileId: String,
        items: List<SupabaseBackend.SchoolItem>
    ): Int {
        createChannel(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "seen_ids_" + profileId
        val baselineKey = "baseline_" + profileId
        val currentIds = items.map { it.id }.toSet()
        val previouslySeen = prefs.getStringSet(key, emptySet()).orEmpty()

        if (!prefs.getBoolean(baselineKey, false)) {
            prefs.edit()
                .putStringSet(key, currentIds)
                .putBoolean(baselineKey, true)
                .apply()
            return 0
        }

        // Keep unseen IDs pending if Android notifications are disabled, so enabling
        // permission later does not silently discard announcements received in the meantime.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return 0

        val fresh = items.filter { it.id !in previouslySeen }
        fresh.asReversed().forEach { item ->
            post(context, item)
        }

        // Store a bounded recent window; the server feed is itself limited to 100 rows.
        prefs.edit().putStringSet(key, currentIds).apply()
        return fresh.size
    }

    fun processWorkflowSync(
        context: Context,
        profileId: String,
        items: List<SupabaseBackend.UserNotification>
    ): Int {
        createChannel(context)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = "workflow_seen_ids_" + profileId
        val baselineKey = "workflow_baseline_" + profileId
        val currentIds = items.map { it.id }.toSet()
        val previouslySeen = prefs.getStringSet(key, emptySet()).orEmpty()
        if (!prefs.getBoolean(baselineKey, false)) {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return 0
            // On first sync, notify only unread workflow items, capped to avoid a burst.
            val unread = items.filter { it.readAt == null }.take(10)
            unread.asReversed().forEach { item -> postWorkflow(context, item) }
            prefs.edit().putStringSet(key, currentIds).putBoolean(baselineKey, true).apply()
            return unread.size
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return 0
        val fresh = items.filter { it.id !in previouslySeen }
        fresh.asReversed().forEach { item -> postWorkflow(context, item) }
        prefs.edit().putStringSet(key, currentIds).apply()
        return fresh.size
    }

    private fun postWorkflow(context: Context, item: SupabaseBackend.UserNotification) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ruven_notification_workflow_id", item.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, item.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setColor(BLUE)
            .setContentTitle(item.title)
            .setContentText(item.body.ifBlank { "افتح RUVEN للاطلاع على التفاصيل." })
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(item.title)
                    .bigText(item.body.ifBlank { "افتح RUVEN للاطلاع على التفاصيل." })
                    .setSummaryText("RUVEN • تنبيه مدرسي")
            )
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup("ruven_school_updates")
            .build()
        NotificationManagerCompat.from(context).notify(item.id.hashCode(), notification)
    }

    private fun post(context: Context, item: SupabaseBackend.SchoolItem) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val (kind, emoji) = when (item.itemType.lowercase()) {
            "homework" -> "واجب جديد" to "📚"
            "preparation" -> "تحضير جديد" to "📝"
            "timetable" -> "تحديث الجدول" to "🗓"
            "report" -> "تحديث مدرسي" to "🔔"
            else -> "إعلان مدرسي" to "📣"
        }
        val subtitle = buildList {
            item.subjectName?.takeIf { it.isNotBlank() }?.let { add("المادة: $it") }
            item.className?.takeIf { it.isNotBlank() }?.let { add("الشعبة: $it") }
            item.body.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" • ").ifBlank { "افتح RUVEN للاطلاع على التفاصيل." }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("ruven_notification_item_id", item.id)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setColor(BLUE)
            .setContentTitle("$emoji  $kind")
            .setContentText(item.title)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle("$emoji  $kind")
                    .bigText(item.title + "\n\n" + subtitle)
                    .setSummaryText("RUVEN • منظومتك المدرسية")
            )
            .setContentIntent(pendingIntent)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setGroup("ruven_school_updates")
            .build()

        NotificationManagerCompat.from(context).notify(item.id.hashCode(), notification)
    }
}
