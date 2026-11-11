package com.example.schedule.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

object WidgetUpdater {
    /** Перерисовать все виджеты. Вызываем после любого изменения данных. */
    suspend fun refreshNow(ctx: Context) = ScheduleWidget().updateAll(ctx.applicationContext)

    /** Будильник на ближайшую полночь по местному времени: «Сегодня/Завтра» в виджете меняются. */
    fun scheduleMidnight(ctx: Context) {
        val next = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 1_000L
        val am = ctx.getSystemService(AlarmManager::class.java)
        val pi = midnightIntent(ctx)
        if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }

    fun cancelMidnight(ctx: Context) {
        ctx.getSystemService(AlarmManager::class.java).cancel(midnightIntent(ctx))
    }

    private fun midnightIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
        ctx, 7001, Intent(ctx, MidnightReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

/** Срабатывает в полночь: обновляет виджет и заводит будильник на следующую полночь. */
class MidnightReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdater.refreshNow(app)
                WidgetUpdater.scheduleMidnight(app)
            } finally {
                result.finish()
            }
        }
    }
}
