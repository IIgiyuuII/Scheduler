package com.example.schedule.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/** Событие, разобранное из фразы. */
data class ParsedEvent(
    val title: String,
    val start: ZonedDateTime,
    val durationMinutes: Long = 60,
    val location: String? = null,
) {
    val end: ZonedDateTime get() = start.plusMinutes(durationMinutes)
}

/** Результат разбора: полный, частичный (для ручной формы) или неудачный. */
sealed interface ParseResult {
    data class Success(val event: ParsedEvent) : ParseResult
    data class Partial(
        val title: String?,
        val date: LocalDate?,
        val time: LocalTime?,
        val location: String?,
    ) : ParseResult
    object Failed : ParseResult
}

/**
 * Чистый Kotlin, без Android. «Сейчас» и часовой пояс приходят параметром now,
 * поэтому тесты детерминированы.
 *
 * Порядок извлечения: место -> «через N ...» -> дата -> время -> остаток = название.
 * Каждая найденная часть вырезается из строки, то, что осталось, и есть название.
 */
object ScheduleParser {

    private const val PART = "утра|дня|вечера|ночи"
    private val FLAGS = "" // (?U) на Android (ICU) не поддерживается, поэтому буквы задаём явно

    private val LOCATION = Regex(
        FLAGS + """(?:(?<![\p{L}\p{N}_])(?:в|на)\s+)?(\d+\s+аудитори[\p{L}\p{N}_]*|аудитори[\p{L}\p{N}_]*\s+\d+[\p{L}\p{N}_]?|ауд\.?\s*\d+[\p{L}\p{N}_]?|кабинет[\p{L}\p{N}_]*\s+\d+[\p{L}\p{N}_]?)"""
    )
    private val REL = Regex(
        FLAGS + """(?<![\p{L}\p{N}_])через\s+(?:(\d+)\s*)?(мин[\p{L}\p{N}_]*|час[\p{L}\p{N}_]*|дн[\p{L}\p{N}_]*|ден[\p{L}\p{N}_]*|недел[\p{L}\p{N}_]*)"""
    )
    private val DATE_WORD = Regex(FLAGS + """(?<![\p{L}\p{N}_])(сегодня|завтра|послезавтра)(?![\p{L}\p{N}_])""")
    private val DATE_ABS = Regex(
        FLAGS + """(?:(?<![\p{L}\p{N}_])(?:на|в)\s+)?(?<![\p{L}\p{N}_])(\d{1,2})\s+(января|февраля|марта|апреля|мая|июня|июля|августа|сентября|октября|ноября|декабря)(?![\p{L}\p{N}_])"""
    )
    private val DATE_WEEKDAY = Regex(
        FLAGS + """(?:(?<![\p{L}\p{N}_])в\s+)?(?:(?<![\p{L}\p{N}_])(следующ[\p{L}\p{N}_]+)\s+)?(?<![\p{L}\p{N}_])(понедельник|вторник|среда|среду|четверг|пятница|пятницу|суббота|субботу|воскресенье)(?![\p{L}\p{N}_])"""
    )
    private val TIME_SPECIAL = Regex(FLAGS + """(?:(?<![\p{L}\p{N}_])в\s+)?(?<![\p{L}\p{N}_])(полдень|полночь)(?![\p{L}\p{N}_])""")
    private val TIME_HM = Regex(
        FLAGS + """(?:(?<![\p{L}\p{N}_])(?:в|к)\s+)?(?<![\d:.])(\d{1,2})[:.](\d{2})(?!\d)(?:\s+($PART))?"""
    )
    private val TIME_H = Regex(
        FLAGS + """(?<![\p{L}\p{N}_])(?:в|к)\s+(\d{1,2})(?![\d:])(?:\s*час[\p{L}\p{N}_]*)?(?:\s+($PART))?"""
    )
    private val TIME_WORD = Regex(
        FLAGS + """(?<![\p{L}\p{N}_])(?:в|к)\s+(час|один|два|три|четыре|пять|шесть|семь|восемь|девять|десять|одиннадцать|двенадцать)(?![\p{L}\p{N}_])(?:\s+час[\p{L}\p{N}_]*)?(?:\s+($PART))?"""
    )

    private val MONTHS = listOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val NUMBER_WORDS = mapOf(
        "час" to 1, "один" to 1, "два" to 2, "три" to 3, "четыре" to 4, "пять" to 5,
        "шесть" to 6, "семь" to 7, "восемь" to 8, "девять" to 9, "десять" to 10,
        "одиннадцать" to 11, "двенадцать" to 12,
    )

    fun parse(text: String, now: ZonedDateTime): ParseResult {
        var rest = " " + text.lowercase().replace('ё', 'е').replace(Regex("[,;!?]"), " ") + " "
        fun cut(m: MatchResult) { rest = rest.replaceRange(m.range, " ") }

        val today = now.toLocalDate()
        var date: LocalDate? = null
        var time: LocalTime? = null
        var isMidnight = false

        // 1. Место
        var location: String? = null
        LOCATION.find(rest)?.let { m ->
            location = m.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            cut(m)
        }

        // 2. «через 2 часа / 3 дня / неделю»
        var fromRelative = false
        val rel = REL.find(rest)
        if (rel != null) {
            val n = rel.groupValues[1].ifEmpty { "1" }.toLong()
            val unit = rel.groupValues[2]
            when {
                unit.startsWith("мин") -> now.plusMinutes(n).let { date = it.toLocalDate(); time = it.toLocalTime().withSecond(0).withNano(0) }
                unit.startsWith("час") -> now.plusHours(n).let { date = it.toLocalDate(); time = it.toLocalTime().withSecond(0).withNano(0) }
                unit.startsWith("д") -> date = today.plusDays(n)
                else -> date = today.plusWeeks(n)
            }
            if (unit.startsWith("мин") || unit.startsWith("час")) fromRelative = true
            cut(rel)
        }

        // 3. Дата
        if (date == null) {
            val w = DATE_WORD.find(rest)
            val a = DATE_ABS.find(rest)
            val d = DATE_WEEKDAY.find(rest)
            when {
                w != null -> {
                    date = today.plusDays(
                        when (w.groupValues[1]) { "сегодня" -> 0L; "завтра" -> 1L; else -> 2L }
                    )
                    cut(w)
                }
                a != null -> {
                    val day = a.groupValues[1].toInt()
                    val month = MONTHS.indexOf(a.groupValues[2]) + 1
                    date = runCatching {
                        val d0 = LocalDate.of(today.year, month, day)
                        if (d0.isBefore(today)) d0.plusYears(1) else d0
                    }.getOrNull()          // 31 июня и т.п. -> дата не распознана
                    cut(a)
                }
                d != null -> {
                    val dow = weekday(d.groupValues[2])
                    date = if (d.groupValues[1].isNotEmpty()) {
                        // «следующую пятницу» = пятница следующей недели (неделя с понедельника)
                        today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                            .with(TemporalAdjusters.nextOrSame(dow))
                    } else {
                        today.with(TemporalAdjusters.next(dow)) // ближайший будущий
                    }
                    cut(d)
                }
            }
        }

        // 4. Время (если не пришло из «через N часов»)
        if (!fromRelative) {
            val sp = TIME_SPECIAL.find(rest)
            val hm = TIME_HM.find(rest)
            val h = TIME_H.find(rest)
            val wd = TIME_WORD.find(rest)
            when {
                sp != null -> {
                    isMidnight = sp.groupValues[1] == "полночь"
                    time = if (isMidnight) LocalTime.MIDNIGHT else LocalTime.NOON
                    cut(sp)
                }
                hm != null -> {
                    time = resolve(hm.groupValues[1].toInt(), hm.groupValues[2].toInt(),
                        hm.groupValues[3].ifEmpty { null }, guessPm = false)
                    cut(hm)
                }
                h != null -> {
                    time = resolve(h.groupValues[1].toInt(), 0,
                        h.groupValues[2].ifEmpty { null }, guessPm = true)
                    cut(h)
                }
                wd != null -> {
                    time = resolve(NUMBER_WORDS.getValue(wd.groupValues[1]), 0,
                        wd.groupValues[2].ifEmpty { null }, guessPm = true)
                    cut(wd)
                }
            }
        }

        val title = cleanTitle(rest)
        var d = date
        val t = time
        if (d == null && t == null) return ParseResult.Failed

        // «в 9 зарядка» без даты: сегодня, если время ещё не прошло, иначе завтра
        if (d == null) {
            d = if (t!!.isAfter(now.toLocalTime())) today else today.plusDays(1)
        } else if (isMidnight) {
            d = d.plusDays(1) // «сегодня в полночь» = 00:00 следующих суток
        }

        return if (t != null && title != null) {
            ParseResult.Success(ParsedEvent(title, ZonedDateTime.of(d, t, now.zone), location = location))
        } else {
            ParseResult.Partial(title, d, t, location)
        }
    }

    /** Часы + дополнение суток («дня», «вечера»). guessPm: «в 3» без пояснения = 15:00. */
    private fun resolve(hour: Int, minute: Int, part: String?, guessPm: Boolean): LocalTime? {
        var h = hour
        when (part) {
            "утра", "ночи" -> if (h == 12) h = 0
            "дня", "вечера" -> if (h in 1..11) h += 12
            null -> if (guessPm && h in 1..7) h += 12
        }
        return if (h in 0..23 && minute in 0..59) LocalTime.of(h, minute) else null
    }

    private fun weekday(s: String): DayOfWeek = when {
        s.startsWith("понед") -> DayOfWeek.MONDAY
        s.startsWith("вторн") -> DayOfWeek.TUESDAY
        s.startsWith("сред") -> DayOfWeek.WEDNESDAY
        s.startsWith("четв") -> DayOfWeek.THURSDAY
        s.startsWith("пятн") -> DayOfWeek.FRIDAY
        s.startsWith("субб") -> DayOfWeek.SATURDAY
        else -> DayOfWeek.SUNDAY
    }

    private fun cleanTitle(s: String): String? {
        val stop = setOf("в", "на", "к", "по")
        val words = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toMutableList()
        while (words.isNotEmpty() && words.first() in stop) words.removeAt(0)
        while (words.isNotEmpty() && words.last() in stop) words.removeAt(words.lastIndex)
        return words.joinToString(" ").ifEmpty { null }?.replaceFirstChar { it.uppercase() }
    }
}
