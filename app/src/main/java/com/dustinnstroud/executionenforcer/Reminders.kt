package com.dustinnstroud.executionenforcer

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

private const val CHANNEL_ID = "execution-enforcer-checkins"

object ReminderScheduler {
    fun scheduleAll(context: Context) {
        val settings = SettingsStore(context).load()
        if (!settings.remindersEnabled) return
        ensureChannel(context)
        schedule(context, requestCode = 101, phase = "morning", time = LocalTime.of(8, 0))
        schedule(context, requestCode = 102, phase = "midday", time = LocalTime.of(13, 0))
        schedule(context, requestCode = 103, phase = "evening", time = LocalTime.of(20, 0))
    }

    private fun schedule(context: Context, requestCode: Int, phase: String, time: LocalTime) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, CheckInReminderReceiver::class.java).putExtra("phase", phase)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val triggerAt = nextTriggerMillis(time)
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
    }

    private fun nextTriggerMillis(time: LocalTime): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var next = now.withHour(time.hour).withMinute(time.minute).withSecond(0).withNano(0)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(zone).toInstant().toEpochMilli()
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Execution Sensei Check-ins",
                NotificationManager.IMPORTANCE_HIGH,
            )
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

class CheckInReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val phase = intent.getStringExtra("phase") ?: "check-in"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("$phase check-in due")
            .setContentText("Stop drifting. Open Execution Sensei and recommit cleanly.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(phase.hashCode(), notification)
        ReminderScheduler.scheduleAll(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            ReminderScheduler.scheduleAll(context)
        }
    }
}
