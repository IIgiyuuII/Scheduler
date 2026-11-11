package com.example.schedule.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.schedule.data.AppDb
import com.example.schedule.data.EventEntity
import com.example.schedule.domain.ParseResult
import com.example.schedule.domain.ScheduleParser
import com.example.schedule.reminder.AlarmScheduler
import com.example.schedule.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

class EventViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).eventDao()

    val events = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun parse(text: String): ParseResult = ScheduleParser.parse(text, ZonedDateTime.now())

    fun save(title: String, place: String, start: ZonedDateTime, remindMinutes: Int = 15) {
        viewModelScope.launch(Dispatchers.IO) {
            val e = EventEntity(
                title = title.trim(),
                location = place.trim().ifEmpty { null },
                startUtcMillis = start.toInstant().toEpochMilli(),
                endUtcMillis = start.plusHours(1).toInstant().toEpochMilli(),
                zoneId = start.zone.id,
                remindMinutes = remindMinutes,
            )
            val id = dao.insert(e)
            AlarmScheduler.schedule(getApplication(), e.copy(id = id))
            WidgetUpdater.refreshNow(getApplication())
        }
    }

    fun update(old: EventEntity, title: String, place: String, start: ZonedDateTime, remindMinutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val duration = old.endUtcMillis - old.startUtcMillis
            val startMs = start.toInstant().toEpochMilli()
            val e = old.copy(
                title = title.trim(),
                location = place.trim().ifEmpty { null },
                startUtcMillis = startMs,
                endUtcMillis = startMs + duration,
                zoneId = start.zone.id,
                remindMinutes = remindMinutes,
            )
            dao.update(e)
            AlarmScheduler.cancel(getApplication(), e.id)
            AlarmScheduler.schedule(getApplication(), e)
            WidgetUpdater.refreshNow(getApplication())
        }
    }

    fun delete(e: EventEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            AlarmScheduler.cancel(getApplication(), e.id)
            dao.delete(e)
            WidgetUpdater.refreshNow(getApplication())
        }
    }
}
