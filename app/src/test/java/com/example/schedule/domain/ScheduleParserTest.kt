package com.example.schedule.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleParserTest {
    private val zone = ZoneId.of("Europe/Helsinki")
    // Понедельник, 19 октября 2026, 10:00
    private val now = ZonedDateTime.of(2026, 10, 19, 10, 0, 0, 0, zone)

    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) =
        ZonedDateTime.of(y, m, d, h, min, 0, 0, zone)

    private fun ok(text: String): ParsedEvent {
        val r = ScheduleParser.parse(text, now)
        assertTrue("Ожидали Success для «$text», получили $r", r is ParseResult.Success)
        return (r as ParseResult.Success).event
    }

    @Test fun tomorrowWithTime() {
        val e = ok("завтра в 15:00 консультация")
        assertEquals(at(2026, 10, 20, 15), e.start)
        assertEquals("Консультация", e.title)
    }

    @Test fun dayAfterTomorrow() {
        val e = ok("послезавтра в 9 сдать лабу")
        assertEquals(at(2026, 10, 21, 9), e.start)
        assertEquals("Сдать лабу", e.title)
    }

    @Test fun nextFridayWithLocation() {
        val e = ok("в следующую пятницу в 18 встреча в 305 аудитории")
        assertEquals(at(2026, 10, 30, 18), e.start)
        assertEquals("305 аудитории", e.location)
        assertEquals("Встреча", e.title)
    }

    @Test fun weekdayOnMondayMeansNextMonday() {
        val e = ok("в понедельник в полдень обед")
        assertEquals(at(2026, 10, 26, 12), e.start)
    }

    @Test fun relativeHours() {
        val e = ok("через 2 часа созвон")
        assertEquals(at(2026, 10, 19, 12), e.start)
        assertEquals("Созвон", e.title)
    }

    @Test fun absoluteDate() {
        assertEquals(at(2026, 10, 25, 12, 30), ok("25 октября в 12:30 экзамен").start)
    }

    @Test fun midnightMeansEndOfDay() {
        assertEquals(at(2026, 10, 20, 0), ok("сегодня в полночь салют").start)
    }

    @Test fun dateInPastRollsToNextYear() {
        assertEquals(at(2027, 1, 31, 10), ok("31 января в 10 приём").start)
    }

    @Test fun hourWithoutDayPartIsAfternoon() {
        assertEquals(at(2026, 10, 20, 15), ok("завтра в три консультация").start)
        assertEquals(at(2026, 10, 20, 3), ok("завтра в 3 утра рейс").start)
    }

    @Test fun timeOnlyAlreadyPassedMeansTomorrow() {
        assertEquals(at(2026, 10, 20, 9), ok("в 9 зарядка").start)
    }

    @Test fun missingTimeGivesPartial() {
        val r = ScheduleParser.parse("завтра сдать отчет", now)
        assertTrue(r is ParseResult.Partial)
        r as ParseResult.Partial
        assertEquals(LocalDate.of(2026, 10, 20), r.date)
        assertNull(r.time)
        assertEquals("Сдать отчет", r.title)
    }

    @Test fun garbageFails() {
        assertEquals(ParseResult.Failed, ScheduleParser.parse("привет как дела", now))
    }

    // ---- Дополнительные случаи: относительные даты, «следующий», полночь, 12-часовой формат ----

    @Test fun relativeMinutesCrossMidnight() {
        val late = ZonedDateTime.of(2026, 10, 19, 23, 50, 0, 0, zone)
        val r = ScheduleParser.parse("через 30 минут позвонить", late) as ParseResult.Success
        assertEquals(at(2026, 10, 20, 0, 20), r.event.start)
    }

    @Test fun relativeDaysWithTime() {
        assertEquals(at(2026, 10, 22, 8), ok("через 3 дня в 8 зарядка").start)
    }

    @Test fun nextMondayKeyword() {
        val e = ok("в следующий понедельник в 10 планерка")
        assertEquals(at(2026, 10, 26, 10), e.start)
        assertEquals("Планерка", e.title)
    }

    @Test fun tomorrowMidnightIsEndOfTomorrow() {
        assertEquals(at(2026, 10, 21, 0), ok("завтра в полночь праздник").start)
    }

    @Test fun noonKeyword() {
        assertEquals(at(2026, 10, 20, 12), ok("завтра в полдень обед").start)
    }

    @Test fun eveningDayPart() {
        assertEquals(at(2026, 10, 20, 19), ok("завтра в 7 вечера ужин").start)
    }
}
