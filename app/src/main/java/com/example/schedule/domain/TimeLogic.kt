package com.example.schedule.domain

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object EventTime {
    /**
     * Подпись «исходное время», если пояс события сейчас даёт другое время на часах, чем пояс устройства.
     * Пример: событие создали в Хельсинки на 15:00, телефон переехал в Лондон -> «15:00 (Europe/Helsinki)».
     * Момент события не меняется, меняется только то, как он показан.
     */
    fun originNote(startUtcMillis: Long, eventZoneId: String, deviceZone: ZoneId): String? {
        val eventZone = runCatching { ZoneId.of(eventZoneId) }.getOrNull() ?: return null
        val instant = Instant.ofEpochMilli(startUtcMillis)
        if (eventZone.rules.getOffset(instant) == deviceZone.rules.getOffset(instant)) return null
        val time = instant.atZone(eventZone).format(DateTimeFormatter.ofPattern("HH:mm"))
        return "$time (${eventZone.id})"
    }

    /** Ближайшая местная полночь в указанном поясе (в день перевода часов сутки бывают 23 или 25 часов). */
    fun nextMidnight(nowMillis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            .plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
}

sealed interface ReminderPlan {
    data class At(val triggerAtMillis: Long) : ReminderPlan
    object Skip : ReminderPlan
}

/** Решает, когда ставить напоминание. Одинаково работает при создании события и после перезагрузки. */
object ReminderPlanner {
    fun plan(nowMillis: Long, startMillis: Long, remindMinutes: Int, notified: Boolean): ReminderPlan {
        if (startMillis <= nowMillis) return ReminderPlan.Skip              // событие уже началось
        val trigger = startMillis - remindMinutes * 60_000L
        if (trigger > nowMillis) return ReminderPlan.At(trigger)            // обычный случай
        // Время напоминания уже прошло. Если уведомление не показывали (телефон был выключен
        // или событие создано впритык) — покажем сразу, иначе не повторяемся.
        return if (notified) ReminderPlan.Skip else ReminderPlan.At(nowMillis + 1_000L)
    }
}
