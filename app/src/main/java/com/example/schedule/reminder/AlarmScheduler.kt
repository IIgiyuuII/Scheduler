package com.example.schedule.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.schedule.data.EventEntity
import com.example.schedule.domain.ReminderPlan
import com.example.schedule.domain.ReminderPlanner

object AlarmScheduler {
    /** Ставит (или заново ставит) напоминание. Повторный вызов заменяет прежний будильник того же события. */
    fun schedule(ctx: Context, e: EventEntity) {
        val plan = ReminderPlanner.plan(System.currentTimeMillis(), e.startUtcMillis, e.remindMinutes, e.notified)
        if (plan !is ReminderPlan.At) return
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = pending(ctx, e.id, e.title)
        // С Android 12 точные будильники требуют разрешения; без него — неточный
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerAtMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerAtMillis, pi)
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
