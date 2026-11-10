package com.example.schedule.domain

data class TimeSlot(val id: Long, val title: String, val startMillis: Long, val endMillis: Long)

object ConflictChecker {
    /**
     * Интервалы пересекаются, если каждый начинается раньше, чем заканчивается другой.
     * Строгие неравенства: событие 10:00–11:00 и 11:00–12:00 не конфликтуют.
     * ignoreId нужен при редактировании, чтобы событие не конфликтовало с собой.
     */
    fun findConflicts(
        startMillis: Long,
        endMillis: Long,
        existing: List<TimeSlot>,
        ignoreId: Long? = null,
    ): List<TimeSlot> = existing.filter {
        it.id != ignoreId && startMillis < it.endMillis && it.startMillis < endMillis
    }
}
