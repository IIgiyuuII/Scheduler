package com.example.schedule.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat

/** Событие, прочитанное из системного календаря. */
data class SystemEvent(
    val eventId: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val location: String?,
    val timeZone: String?,
    val allDay: Boolean,
)

sealed interface CalendarState {
    object Loading : CalendarState
    data class Ready(val events: List<SystemEvent>) : CalendarState
    data class Error(val message: String) : CalendarState
}

/**
 * Работа с системным календарём через Calendar Provider (ContentResolver).
 * Все методы блокирующие: вызывать только вне главного потока.
 * Без разрешений запись возвращает null и ничего не делает, а не падает.
 */
class CalendarRepository(private val ctx: Context) {
    private val resolver get() = ctx.contentResolver

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    fun canWrite() = granted(Manifest.permission.READ_CALENDAR) && granted(Manifest.permission.WRITE_CALENDAR)
    fun canRead() = granted(Manifest.permission.READ_CALENDAR)

    /** Первый видимый календарь, в который можно писать; основной аккаунт — в приоритете. */
    private fun writableCalendarId(): Long? {
        val projection = arrayOf(Calendars._ID)
        val selection = "${Calendars.CALENDAR_ACCESS_LEVEL} >= ${Calendars.CAL_ACCESS_CONTRIBUTOR} " +
            "AND ${Calendars.VISIBLE} = 1"
        resolver.query(
            Calendars.CONTENT_URI, projection, selection, null,
            "${Calendars.IS_PRIMARY} DESC, ${Calendars._ID} ASC",
        )?.use { c -> if (c.moveToFirst()) return c.getLong(0) }
        return null
    }

    private fun values(e: EventEntity) = ContentValues().apply {
        put(Events.TITLE, e.title)
        put(Events.EVENT_LOCATION, e.location)
        put(Events.DTSTART, e.startUtcMillis)
        put(Events.DTEND, e.endUtcMillis)
        put(Events.EVENT_TIMEZONE, e.zoneId)
    }

    /**
     * Создаёт событие в календаре, а если оно там уже есть (calendarEventId) — обновляет.
     * Возвращает id события в календаре или null, если записать не удалось.
     * Напоминание в календарь не добавляем: за него отвечает само приложение,
     * иначе пользователь получил бы два уведомления.
     */
    fun upsert(e: EventEntity): Long? {
        if (!canWrite()) return null
        return try {
            val existing = e.calendarEventId
            if (existing != null) {
                val uri = ContentUris.withAppendedId(Events.CONTENT_URI, existing)
                if (resolver.update(uri, values(e), null, null) > 0) return existing
                // Событие удалили в самом календаре: создаём заново
            }
            val calendarId = writableCalendarId() ?: return null
            val v = values(e).apply { put(Events.CALENDAR_ID, calendarId) }
            resolver.insert(Events.CONTENT_URI, v)?.let { ContentUris.parseId(it) }
        } catch (_: SecurityException) {
            null
        }
    }

    fun delete(calendarEventId: Long?) {
        if (calendarEventId == null || !canWrite()) return
        try {
            resolver.delete(ContentUris.withAppendedId(Events.CONTENT_URI, calendarEventId), null, null)
        } catch (_: SecurityException) {
        }
    }

    /** События календаря на ближайшие [days] дней (повторяющиеся приходят отдельными вхождениями). */
    fun readUpcoming(days: Int = 30): List<SystemEvent> {
        if (!canRead()) throw SecurityException("READ_CALENDAR не выдано")
        val from = System.currentTimeMillis()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, from + days * 86_400_000L)
        }.build()
        val projection = arrayOf(
            Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END,
            Instances.EVENT_LOCATION, Instances.ALL_DAY, Instances.EVENT_TIMEZONE,
        )
        val result = mutableListOf<SystemEvent>()
        resolver.query(uri, projection, null, null, "${Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                result += SystemEvent(
                    eventId = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(без названия)",
                    start = c.getLong(2),
                    end = c.getLong(3),
                    location = c.getString(4)?.takeIf { it.isNotBlank() },
                    timeZone = c.getString(6),
                    allDay = c.getInt(5) == 1,
                )
            }
        }
        return result
    }
}
