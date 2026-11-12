package com.example.schedule.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.example.schedule.R
import com.example.schedule.data.EventEntity
import com.example.schedule.domain.ConflictChecker
import com.example.schedule.domain.ParseResult
import com.example.schedule.domain.TimeSlot
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

private val examples = listOf(
    "завтра в 15:00 консультация",
    "послезавтра в 9 сдать лабу",
    "в пятницу в 18 встреча в 305 аудитории",
    "через 2 часа созвон",
    "25 ноября в 12:30 экзамен",
)

@Composable
fun AddScreen(vm: EventViewModel, access: CalendarAccess, modifier: Modifier = Modifier) {
    val events by vm.events.collectAsState()
    var phrase by rememberSaveable { mutableStateOf("") }
    var showForm by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var place by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }   // ГГГГ-ММ-ДД
    var time by rememberSaveable { mutableStateOf("") }   // ЧЧ:ММ
    var note by rememberSaveable { mutableStateOf("") }
    var remind by rememberSaveable { mutableStateOf("15") }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!access.granted) {
            Column {
                Banner("События не попадут в календарь телефона: нет доступа к нему", isError = false)
                TextButton(onClick = if (access.permanentlyDenied) access.onOpenSettings else access.onRequest) {
                    Text(if (access.permanentlyDenied) "Открыть настройки" else "Разрешить доступ")
                }
            }
        }
        AppField(phrase, { phrase = it }, "Что и когда?", R.drawable.ic_sparkle)

        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            examples.forEach { ex -> SuggestionPill(ex) { phrase = ex } }
        }

        Button(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
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
        ) {
            AppIcon(R.drawable.ic_sparkle, size = 20.dp)
            Spacer(Modifier.width(8.dp))
            Text("Разобрать", style = MaterialTheme.typography.titleMedium)
        }

        if (showForm) {
            val start = runCatching {
                ZonedDateTime.of(LocalDate.parse(date.trim()), LocalTime.parse(time.trim()), ZoneId.systemDefault())
            }.getOrNull()
            val conflicts = if (start == null) emptyList() else ConflictChecker.findConflicts(
                start.toInstant().toEpochMilli(),
                start.plusHours(1).toInstant().toEpochMilli(),
                events.map { TimeSlot(it.id, it.title, it.startUtcMillis, it.endUtcMillis) },
            )

            Card(
                Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(R.drawable.ic_calendar, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Новое событие", style = MaterialTheme.typography.titleMedium)
                    }
                    if (note.isNotEmpty()) Banner(note, isError = false)
                    AppField(title, { title = it }, "Название", null)
                    AppField(place, { place = it }, "Место", R.drawable.ic_location)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppField(date, { date = it }, "Дата (ГГГГ-ММ-ДД)", R.drawable.ic_calendar, Modifier.weight(1f))
                        AppField(time, { time = it }, "Время", R.drawable.ic_clock, Modifier.weight(0.8f))
                    }
                    AppField(remind, { remind = it }, "Напомнить за (мин, 0 — в момент начала)", R.drawable.ic_bell)
                    if (conflicts.isNotEmpty()) {
                        Banner("Пересекается с: " + conflicts.joinToString { it.title }, isError = true)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(onClick = { showForm = false }) { Text("Отмена") }
                        Button(
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(14.dp),
                            enabled = start != null && title.isNotBlank() && remind.trim().toIntOrNull() != null,
                            onClick = {
                                vm.save(title, place, start!!, remind.trim().toInt())
                                showForm = false; phrase = ""
                            },
                        ) {
                            AppIcon(R.drawable.ic_check, size = 20.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Подтвердить")
                        }
                    }
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
fun AgendaScreen(
    vm: EventViewModel,
    openEventId: Long,
    onOpenHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val events by vm.events.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(0) }            // 0 — день, 1 — неделя
    var anchorIso by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var editingId by rememberSaveable { mutableLongStateOf(-1L) }

    // Событие, открытое из виджета: переходим на его день и показываем карточку для просмотра/правки
    LaunchedEffect(openEventId, events) {
        if (openEventId >= 0) {
            events.firstOrNull { it.id == openEventId }?.let { e ->
                query = ""
                mode = 0
                anchorIso = e.startLocal().toLocalDate().toString()
                editingId = e.id
                onOpenHandled()
            }
        }
    }

    val ru = Locale("ru")
    val dayFmt = DateTimeFormatter.ofPattern("EEEE, d MMMM", ru)
    val shortFmt = DateTimeFormatter.ofPattern("d MMM", ru)

    val today = LocalDate.now()
    val anchor = LocalDate.parse(anchorIso)
    val searching = query.isNotBlank()
    val rangeStart = if (mode == 0) anchor else anchor.with(DayOfWeek.MONDAY)
    val rangeEnd = if (mode == 0) rangeStart else rangeStart.plusDays(6)
    val step = if (mode == 0) 1L else 7L

    fun dayTitle(d: LocalDate): String {
        val t = d.format(dayFmt).replaceFirstChar { it.uppercase() }
        return if (d == today) "Сегодня · $t" else t
    }

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

    Column(
        modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AppField(query, { query = it }, "Поиск по событиям", R.drawable.ic_search)

        if (!searching) {
            Segmented(listOf("День", "Неделя"), mode) { mode = it }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = { anchorIso = anchor.minusDays(step).toString() }) {
                    AppIcon(R.drawable.ic_chevron_left)
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .clickable { anchorIso = today.toString() }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(R.drawable.ic_calendar, tint = MaterialTheme.colorScheme.onPrimaryContainer, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (mode == 0) dayTitle(anchor)
                        else rangeStart.format(shortFmt) + " – " + rangeEnd.format(shortFmt),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                IconButton(onClick = { anchorIso = anchor.plusDays(step).toString() }) {
                    AppIcon(R.drawable.ic_chevron_right)
                }
            }
        }

        if (groups.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(top = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AppIcon(R.drawable.ic_calendar, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), size = 72.dp)
                Spacer(Modifier.height(12.dp))
                Text(
                    when {
                        searching -> "Ничего не найдено"
                        mode == 0 -> "На этот день событий нет"
                        else -> "На этой неделе событий нет"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                groups.forEach { (day, list) ->
                    if (mode == 1 || searching) {
                        item(key = "h$day") {
                            Text(
                                dayTitle(day),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    items(list, key = { it.id }) { e ->
                        EventCard(e, onEdit = { editingId = e.id }, onDelete = { vm.delete(e) })
                    }
                }
            }
        }
    }

    events.firstOrNull { it.id == editingId }?.let { e ->
        EditDialog(
            e, events,
            onDismiss = { editingId = -1L },
            onSave = { t, p, st, r -> vm.update(e, t, p, st, r); editingId = -1L },
        )
    }
}

@Composable
private fun EventCard(e: EventEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm")
    val past = e.endUtcMillis < System.currentTimeMillis()

    Card(
        Modifier.fillMaxWidth().alpha(if (past) 0.6f else 1f),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(
                Modifier.width(5.dp).fillMaxHeight()
                    .background(Brush.verticalGradient(listOf(Brand1, Brand2)))
            )
            Column(Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp).width(58.dp)) {
                Text(e.startLocal().format(timeFmt), style = MaterialTheme.typography.titleMedium, color = cs.primary)
                Text(e.endLocal().format(timeFmt), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            }
            Column(Modifier.weight(1f).padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(e.title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                e.location?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(R.drawable.ic_location, tint = cs.onSurfaceVariant, size = 16.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(R.drawable.ic_bell, tint = cs.onSurfaceVariant, size = 16.dp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (e.remindMinutes == 0) "в момент начала" else "за ${e.remindMinutes} мин",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
            Row(Modifier.padding(end = 4.dp).align(Alignment.CenterVertically)) {
                IconButton(onClick = onEdit, modifier = Modifier.size(40.dp)) {
                    AppIcon(R.drawable.ic_edit, tint = cs.primary, size = 20.dp)
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                    AppIcon(R.drawable.ic_delete, tint = cs.error, size = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun EditDialog(
    e: EventEntity,
    events: List<EventEntity>,
    onDismiss: () -> Unit,
    onSave: (String, String, ZonedDateTime, Int) -> Unit,
) {
    val s = e.startLocal()
    var title by rememberSaveable(e.id) { mutableStateOf(e.title) }
    var place by rememberSaveable(e.id) { mutableStateOf(e.location.orEmpty()) }
    var date by rememberSaveable(e.id) { mutableStateOf(s.toLocalDate().toString()) }
    var time by rememberSaveable(e.id) {
        mutableStateOf(s.toLocalTime().withSecond(0).withNano(0).toString())
    }
    var remind by rememberSaveable(e.id) { mutableStateOf(e.remindMinutes.toString()) }

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
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text("Изменить событие") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppField(title, { title = it }, "Название", null)
                AppField(place, { place = it }, "Место", R.drawable.ic_location)
                AppField(date, { date = it }, "Дата (ГГГГ-ММ-ДД)", R.drawable.ic_calendar)
                AppField(time, { time = it }, "Время (ЧЧ:ММ)", R.drawable.ic_clock)
                AppField(remind, { remind = it }, "Напомнить за (мин)", R.drawable.ic_bell)
                if (conflicts.isNotEmpty()) {
                    Banner("Пересекается с: " + conflicts.joinToString { it.title }, isError = true)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = start != null && title.isNotBlank() && remind.trim().toIntOrNull() != null,
                onClick = { onSave(title, place, start!!, remind.trim().toInt()) },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
