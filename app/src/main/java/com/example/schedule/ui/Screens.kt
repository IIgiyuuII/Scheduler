package com.example.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import com.example.schedule.data.EventEntity
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.schedule.domain.ConflictChecker
import com.example.schedule.domain.ParseResult
import com.example.schedule.domain.TimeSlot
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun AddScreen(vm: EventViewModel, modifier: Modifier = Modifier) {
    val events by vm.events.collectAsState()
    var phrase by rememberSaveable { mutableStateOf("") }
    var showForm by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var place by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }   // ГГГГ-ММ-ДД
    var time by rememberSaveable { mutableStateOf("") }   // ЧЧ:ММ
    var note by rememberSaveable { mutableStateOf("") }

    Column(
        modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            phrase, { phrase = it },
            label = { Text("Например: завтра в 15:00 консультация") },
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            enabled = phrase.isNotBlank(),
            onClick = {
                when (val r = vm.parse(phrase)) {
                    is ParseResult.Success -> {
                        title = r.event.title
                        place = r.event.location.orEmpty()
                        date = r.event.start.toLocalDate().toString()
                        time = r.event.start.toLocalTime().toString()
                        note = ""
                    }
                    is ParseResult.Partial -> {
                        title = r.title.orEmpty()
                        place = r.location.orEmpty()
                        date = r.date?.toString().orEmpty()
                        time = r.time?.toString().orEmpty()
                        note = "Разобрано не всё — проверьте поля"
                    }
                    ParseResult.Failed -> {
                        title = phrase; place = ""; date = ""; time = ""
                        note = "Не удалось разобрать фразу — заполните вручную"
                    }
                }
                showForm = true
            },
        ) { Text("Разобрать") }

        if (showForm) {
            val start = runCatching {
                ZonedDateTime.of(LocalDate.parse(date.trim()), LocalTime.parse(time.trim()), ZoneId.systemDefault())
            }.getOrNull()
            val conflicts = if (start == null) emptyList() else ConflictChecker.findConflicts(
                start.toInstant().toEpochMilli(),
                start.plusHours(1).toInstant().toEpochMilli(),
                events.map { TimeSlot(it.id, it.title, it.startUtcMillis, it.endUtcMillis) },
            )

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (note.isNotEmpty()) Text(note, color = MaterialTheme.colorScheme.error)
                    OutlinedTextField(title, { title = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(place, { place = it }, label = { Text("Место") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(date, { date = it }, label = { Text("Дата (ГГГГ-ММ-ДД)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(time, { time = it }, label = { Text("Время (ЧЧ:ММ)") }, modifier = Modifier.fillMaxWidth())
                    if (conflicts.isNotEmpty()) {
                        Text(
                            "Пересекается с: " + conflicts.joinToString { it.title },
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Button(
                        enabled = start != null && title.isNotBlank(),
                        onClick = {
                            vm.save(title, place, start!!)
                            showForm = false; phrase = ""
                        },
                    ) { Text("Подтвердить") }
                }
            }
        }
    }
}

private fun EventEntity.startLocal(): ZonedDateTime =
    Instant.ofEpochMilli(startUtcMillis).atZone(ZoneId.systemDefault())

private fun EventEntity.endLocal(): ZonedDateTime =
    Instant.ofEpochMilli(endUtcMillis).atZone(ZoneId.systemDefault())

@Composable
fun AgendaScreen(vm: EventViewModel, modifier: Modifier = Modifier) {
    val events by vm.events.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(0) }            // 0 — день, 1 — неделя
    var anchorIso by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var editingId by rememberSaveable { mutableLongStateOf(-1L) }

    val ru = Locale("ru")
    val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)
    val shortFmt = DateTimeFormatter.ofPattern("d MMM", ru)
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    val anchor = LocalDate.parse(anchorIso)
    val searching = query.isNotBlank()
    val rangeStart = if (mode == 0) anchor else anchor.with(DayOfWeek.MONDAY)
    val rangeEnd = if (mode == 0) rangeStart else rangeStart.plusDays(6)
    val step = if (mode == 0) 1L else 7L

    // При поиске ищем по всем событиям, иначе показываем выбранный день или неделю
    val visible = if (searching) {
        val q = query.trim()
        events.filter { it.title.contains(q, ignoreCase = true) || it.location?.contains(q, ignoreCase = true) == true }
    } else {
        events.filter {
            val d = it.startLocal().toLocalDate()
            !d.isBefore(rangeStart) && !d.isAfter(rangeEnd)
        }
    }
    val groups = visible.groupBy { it.startLocal().toLocalDate() }.toList()

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            query, { query = it },
            label = { Text("Поиск по событиям") },
            modifier = Modifier.fillMaxWidth(),
        )
        if (!searching) {
            TabRow(selectedTabIndex = mode) {
                Tab(selected = mode == 0, onClick = { mode = 0 }, text = { Text("День") })
                Tab(selected = mode == 1, onClick = { mode = 1 }, text = { Text("Неделя") })
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { anchorIso = anchor.minusDays(step).toString() }) { Text("‹") }
                TextButton(onClick = { anchorIso = LocalDate.now().toString() }) {
                    Text(
                        if (mode == 0) anchor.format(dayFmt)
                        else rangeStart.format(shortFmt) + " – " + rangeEnd.format(shortFmt)
                    )
                }
                TextButton(onClick = { anchorIso = anchor.plusDays(step).toString() }) { Text("›") }
            }
        }

        if (groups.isEmpty()) {
            Text(
                when {
                    searching -> "Ничего не найдено"
                    mode == 0 -> "На этот день событий нет"
                    else -> "На этой неделе событий нет"
                }
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                groups.forEach { (day, list) ->
                    if (mode == 1 || searching) {
                        item(key = "h$day") {
                            Text(
                                day.format(dayFmt).replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    items(list, key = { it.id }) { e ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp)) {
                                Text(e.title, style = MaterialTheme.typography.titleMedium)
                                Text(e.startLocal().format(timeFmt) + " – " + e.endLocal().format(timeFmt))
                                e.location?.let { Text(it) }
                                Row {
                                    TextButton(onClick = { editingId = e.id }) { Text("Изменить") }
                                    TextButton(onClick = { vm.delete(e) }) { Text("Удалить") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    events.firstOrNull { it.id == editingId }?.let { e ->
        EditDialog(
            e, events,
            onDismiss = { editingId = -1L },
            onSave = { t, p, st -> vm.update(e, t, p, st); editingId = -1L },
        )
    }
}

@Composable
private fun EditDialog(
    e: EventEntity,
    events: List<EventEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, ZonedDateTime) -> Unit,
) {
    val s = e.startLocal()
    var title by rememberSaveable(e.id) { mutableStateOf(e.title) }
    var place by rememberSaveable(e.id) { mutableStateOf(e.location.orEmpty()) }
    var date by rememberSaveable(e.id) { mutableStateOf(s.toLocalDate().toString()) }
    var time by rememberSaveable(e.id) {
        mutableStateOf(s.toLocalTime().withSecond(0).withNano(0).toString())
    }

    val start = runCatching {
        ZonedDateTime.of(LocalDate.parse(date.trim()), LocalTime.parse(time.trim()), ZoneId.systemDefault())
    }.getOrNull()
    val conflicts = if (start == null) emptyList() else {
        val startMs = start.toInstant().toEpochMilli()
        ConflictChecker.findConflicts(
            startMs, startMs + (e.endUtcMillis - e.startUtcMillis),
            events.map { TimeSlot(it.id, it.title, it.startUtcMillis, it.endUtcMillis) },
            ignoreId = e.id,   // событие не конфликтует само с собой
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Изменить событие") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(place, { place = it }, label = { Text("Место") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(date, { date = it }, label = { Text("Дата (ГГГГ-ММ-ДД)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(time, { time = it }, label = { Text("Время (ЧЧ:ММ)") }, modifier = Modifier.fillMaxWidth())
                if (conflicts.isNotEmpty()) {
                    Text(
                        "Пересекается с: " + conflicts.joinToString { it.title },
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = start != null && title.isNotBlank(),
                onClick = { onSave(title, place, start!!) },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
