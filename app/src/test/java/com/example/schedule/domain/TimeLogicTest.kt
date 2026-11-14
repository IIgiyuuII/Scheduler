package com.example.schedule.domain

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimeLogicTest {
    private val helsinki = ZoneId.of("Europe/Helsinki")
    private val london = ZoneId.of("Europe/London")
    private val minute = 60_000L

    private fun ms(y: Int, m: Int, d: Int, h: Int, min: Int, zone: ZoneId) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    // ---- часовые пояса ----

    @Test fun momentSurvivesZoneChange() {
        val start = ms(2026, 10, 19, 15, 0, helsinki)
        val inLondon = Instant.ofEpochMilli(start).atZone(london)
        assertEquals(13, inLondon.hour)                                  // на часах другое время
        assertEquals(start, inLondon.toInstant().toEpochMilli())         // а момент тот же
    }

    @Test fun originNoteOnlyWhenOffsetDiffers() {
        val start = ms(2026, 10, 19, 15, 0, helsinki)
        assertNull(EventTime.originNote(start, "Europe/Helsinki", helsinki))
        assertEquals("15:00 (Europe/Helsinki)", EventTime.originNote(start, "Europe/Helsinki", london))
    }

    @Test fun unknownZoneIdIsIgnored() {
        assertNull(EventTime.originNote(0, "Mars/Phobos", london))
    }

    // ---- полночь (виджет) ----

    @Test fun midnightOnDstDayIs25HoursAway() {
        val dayStart = ms(2026, 10, 25, 0, 0, helsinki)                  // в этот день часы переводят назад
        val next = EventTime.nextMidnight(dayStart + 10 * minute, helsinki)
        assertEquals(ms(2026, 10, 26, 0, 0, helsinki), next)
        assertEquals(25 * 60 * minute, next - dayStart)
    }

    @Test fun nextMidnightDependsOnZone() {
        val now = ms(2026, 10, 19, 23, 30, helsinki)
        assertEquals(ms(2026, 10, 20, 0, 0, helsinki), EventTime.nextMidnight(now, helsinki))
        assertEquals(ms(2026, 10, 20, 0, 0, london), EventTime.nextMidnight(now, london))
    }

    // ---- планирование напоминаний (перезагрузка, перевод часов) ----

    @Test fun futureReminderIsPlacedBeforeStart() {
        val now = 1_000_000L
        val start = now + 30 * minute
        assertEquals(
            ReminderPlan.At(start - 15 * minute),
            ReminderPlanner.plan(now, start, remindMinutes = 15, notified = false),
        )
    }

    @Test fun missedReminderFiresNowIfNeverShown() {
        val now = 1_000_000L
        assertEquals(
            ReminderPlan.At(now + 1_000L),
            ReminderPlanner.plan(now, now + 5 * minute, remindMinutes = 15, notified = false),
        )
    }

    @Test fun shownReminderIsNotRepeatedAfterReboot() {
        val now = 1_000_000L
        assertEquals(
            ReminderPlan.Skip,
            ReminderPlanner.plan(now, now + 5 * minute, remindMinutes = 15, notified = true),
        )
    }

    @Test fun startedEventIsSkipped() {
        val now = 1_000_000L
        assertEquals(ReminderPlan.Skip, ReminderPlanner.plan(now, now - minute, 15, notified = false))
    }

    @Test fun clockMovedBackReschedulesEvenIfShown() {
        val now = 1_000_000L
        val start = now + 60 * minute
        assertEquals(
            ReminderPlan.At(start - 15 * minute),
            ReminderPlanner.plan(now, start, 15, notified = true),
        )
    }
}
