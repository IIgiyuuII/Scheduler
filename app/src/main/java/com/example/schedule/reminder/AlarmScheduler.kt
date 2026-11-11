package com.example.schedule.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.schedule.data.EventEntity

object AlarmScheduler {
    fun schedule(ctx: Context, e: EventEntity) {
        val now = System.currentTimeMillis()
        if (e.startUtcMillis <= now) return                       // событие уже прошло
        var triggerAt = e.startUtcMillis - e.remindMinutes * 60_000L
        if (triggerAt <= now) triggerAt = now + 1_000L           // «за N минут» уже прошло: напомнить сразу
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, e.id, e.title)
        // С Android 12 точные будильники требуют разрешения пользователя; без него — неточный
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun cancel(ctx: Context, id: Long) {
        ctx.getSystemService(AlarmManager::class.java).cancel(pending(ctx, id, ""))
    }

    private fun pending(ctx: Context, id: Long, title: String): PendingIntent =
        PendingIntent.getBroadcast(
            ctx, id.toInt(),
            Intent(ctx, ReminderReceiver::class.java).putExtra("id", id).putExtra("title", title),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
