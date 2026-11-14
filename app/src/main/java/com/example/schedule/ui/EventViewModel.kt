package com.example.schedule.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedule.data.AppDb
import com.example.schedule.data.CalendarRepository
import com.example.schedule.data.CalendarState
import com.example.schedule.data.EventEntity
import com.example.schedule.data.SystemEvent
import com.example.schedule.domain.ParseResult
import com.example.schedule.domain.ScheduleParser
import com.example.schedule.reminder.AlarmScheduler
import com.example.schedule.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime

class EventViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).eventDao()
    private val calendar = CalendarRepository(app)

    val events = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _calendarState = MutableStateFlow<CalendarState>(CalendarState.Loading)
    val calendarState: StateFlow<CalendarState> = _calendarState

    fun parse(text: String): ParseResult = ScheduleParser.parse(text, ZonedDateTime.now())

    fun save(title: String, place: String, start: ZonedDateTime, remindMinutes: Int = 15) {
        viewModelScope.launch(Dispatchers.IO) {
            val startMs = start.toInstant().toEpochMilli()
            val draft = EventEntity(
                title = title.trim(),
                location = place.trim().ifEmpty { null },
                startUtcMillis = startMs,
                endUtcMillis = start.plusHours(1).toInstant().toEpochMilli(),
                zoneId = start.zone.id,
                remindMinutes = remindMinutes,
            )
            var e = draft.copy(id = dao.insert(draft))
            // Зеркалим в системный календарь (если выдано разрешение)
            calendar.upsert(e)?.let { e = e.copy(calendarEventId = it); dao.update(e) }
            AlarmScheduler.schedule(getApplication(), e)
            WidgetUpdater.refreshNow(getApplication())
        }
    }

    fun update(old: EventEntity, title: String, place: String, start: ZonedDateTime, remindMinutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val duration = old.endUtcMillis - old.startUtcMillis
            val startMs = start.toInstant().toEpochMilli()
            var e = old.copy(
                title = title.trim(),
                location = place.trim().ifEmpty { null },
                startUtcMillis = startMs,
                endUtcMillis = startMs + duration,
                zoneId = start.zone.id,
                remindMinutes = remindMinutes,
                notified = false,   // время изменилось: напоминание снова актуально
            )
            // Импортированные события не пишем обратно в чужой календарь
            if (e.source == EventEntity.SOURCE_APP) {
                calendar.upsert(e)?.let { e = e.copy(calendarEventId = it) }
            }
            dao.update(e)
            AlarmScheduler.cancel(getApplication(), e.id)
            AlarmScheduler.schedule(getApplication(), e)
            WidgetUpdater.refreshNow(getApplication())
        }
    }

    fun delete(e: EventEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            AlarmScheduler.cancel(getApplication(), e.id)
            // Из календаря удаляем только то, что создало приложение; импортированное не трогаем
            if (e.source == EventEntity.SOURCE_APP) calendar.delete(e.calendarEventId)
            dao.delete(e)
            WidgetUpdater.refreshNow(getApplication())
        }
    }

    /** Читает события системного календаря на 30 дней вперёд. */
    fun loadCalendar() {
        _calendarState.value = CalendarState.Loading
        viewModelScope.launch(Dispatchers.IO) {
            _calendarState.value = try {
                CalendarState.Ready(calendar.readUpcoming())
            } catch (_: SecurityException) {
                CalendarState.Error("Нет доступа к календарю")
            } catch (_: Exception) {
                CalendarState.Error("Не удалось прочитать календарь")
            }
        }
    }

    /** Копия события из системного календаря в приложение. */
    fun importEvent(se: SystemEvent) {
        viewModelScope.launch(Dispatchers.IO) {
            val draft = EventEntity(
                title = se.title,
                location = se.location,
                startUtcMillis = se.start,
                endUtcMillis = se.end,
                zoneId = se.timeZone ?: ZoneId.systemDefault().id,
                calendarEventId = se.eventId,
                source = EventEntity.SOURCE_CALENDAR,
            )
            val e = draft.copy(id = dao.insert(draft))
            AlarmScheduler.schedule(getApplication(), e)
            WidgetUpdater.refreshNow(getApplication())
        }
    }

    /** Записывает в календарь события приложения, которых там ещё нет (например, созданные до выдачи разрешения). */
    fun syncAllToCalendar() {
        viewModelScope.launch(Dispatchers.IO) {
            dao.getAll()
                .filter { it.source == EventEntity.SOURCE_APP && it.calendarEventId == null }
                .forEach { e -> calendar.upsert(e)?.let { dao.update(e.copy(calendarEventId = it)) } }
            loadCalendar()
        }
    }
}
