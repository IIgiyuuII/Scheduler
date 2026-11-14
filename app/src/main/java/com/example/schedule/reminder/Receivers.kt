package com.example.schedule.reminder

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.schedule.data.AppDb
import com.example.schedule.ui.MainActivity
import com.example.schedule.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.TimeZone

/** Срабатывает за N минут до события: показывает уведомление и отмечает событие как «напомнено». */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        val id = intent.getLongExtra("id", -1L)
        showNotification(app, id, intent.getStringExtra("title") ?: "Событие")

        if (id >= 0) {
            val result = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    AppDb.get(app).eventDao().markNotified(id)
                } finally {
                    result.finish()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(ctx: Context, id: Long, title: String) {
        val nm = NotificationManagerCompat.from(ctx)
        nm.createNotificationChannel(
            NotificationChannelCompat.Builder("reminders", NotificationManagerCompat.IMPORTANCE_HIGH)
                .setName("Напоминания").build()
        )
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(ctx, "reminders")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText("Скоро начнётся")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        nm.notify(id.toInt(), n)
    }
}

/**
 * Система стирает будильники при перезагрузке, а при переводе часов или смене пояса «сегодня»
 * и «полночь» сдвигаются. Поэтому на каждое из этих событий заново ставим всё из Room.
 */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val app = ctx.applicationContext
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // JVM кэширует часовой пояс: сбрасываем, чтобы взять новый
                if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) TimeZone.setDefault(null)

                AppDb.get(app).eventDao().getAll().forEach { AlarmScheduler.schedule(app, it) }

                if (WidgetUpdater.hasWidgets(app)) {
                    WidgetUpdater.scheduleMidnight(app)
                    WidgetUpdater.refreshNow(app)
                }
            } finally {
                result.finish()
            }
        }
    }
}
