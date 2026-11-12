package com.example.schedule.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.schedule.R
import com.example.schedule.data.CalendarState
import com.example.schedule.data.SystemEvent
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Состояние разрешений календаря и действия над ними, чтобы не тащить в экраны Activity. */
data class CalendarAccess(
    val granted: Boolean,
    val permanentlyDenied: Boolean,
    val onRequest: () -> Unit,
    val onOpenSettings: () -> Unit,
)

@Composable
fun CalendarScreen(vm: EventViewModel, access: CalendarAccess, modifier: Modifier = Modifier) {
    val state by vm.calendarState.collectAsState()
    val events by vm.events.collectAsState()
    val linked = events.mapNotNull { it.calendarEventId }.toSet()
    val cs = MaterialTheme.colorScheme

    LaunchedEffect(access.granted) { if (access.granted) vm.loadCalendar() }

    Column(
        modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!access.granted) {
            PermissionCard(access)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Ближайшие 30 дней", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { vm.loadCalendar() }) { AppIcon(R.drawable.ic_sync, tint = cs.primary) }
            }
            OutlinedButton(
                onClick = { vm.syncAllToCalendar() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                AppIcon(R.drawable.ic_calendar, size = 20.dp)
                Spacer(Modifier.width(8.dp))
                Text("Записать мои события в календарь")
            }
            when (val s = state) {
                CalendarState.Loading -> Box(
                    Modifier.fillMaxWidth().padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                is CalendarState.Error -> {
                    Banner(s.message, isError = true)
                    Button(onClick = { vm.loadCalendar() }) { Text("Повторить") }
                }

                is CalendarState.Ready -> if (s.events.isEmpty()) {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(R.drawable.ic_calendar, tint = cs.primary.copy(alpha = 0.35f), size = 72.dp)
                        Spacer(Modifier.height(12.dp))
                        Text("В ближайшие 30 дней событий нет", color = cs.onSurfaceVariant)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(s.events, key = { "${it.eventId}-${it.start}" }) { se ->
                            SystemEventCard(se, inApp = se.eventId in linked) { vm.importEvent(se) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(access: CalendarAccess) {
    val cs = MaterialTheme.colorScheme
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        Column(
            Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(R.drawable.ic_calendar, tint = cs.primary, size = 48.dp)
            Text("Доступ к календарю", style = MaterialTheme.typography.titleMedium)
            Text(
                if (access.permanentlyDenied)
                    "Разрешение отклонено. Включите «Календарь» в настройках приложения."
                else
                    "Разрешите доступ, чтобы записывать события в календарь телефона и читать оттуда ваши встречи.",
                color = cs.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(
                onClick = if (access.permanentlyDenied) access.onOpenSettings else access.onRequest,
                shape = RoundedCornerShape(14.dp),
            ) { Text(if (access.permanentlyDenied) "Открыть настройки" else "Разрешить доступ") }
        }
    }
}

@Composable
private fun SystemEventCard(se: SystemEvent, inApp: Boolean, onImport: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val ru = Locale("ru")
    // События «на весь день» хранятся в UTC, остальные показываем в поясе устройства
    val zone = if (se.allDay) ZoneOffset.UTC else ZoneId.systemDefault()
    val start = Instant.ofEpochMilli(se.start).atZone(zone)
    val end = Instant.ofEpochMilli(se.end).atZone(zone)
    val whenText = if (se.allDay) {
        start.format(DateTimeFormatter.ofPattern("EEE, d MMM", ru)) + " · весь день"
    } else {
        start.format(DateTimeFormatter.ofPattern("EEE, d MMM, HH:mm", ru)) + " – " +
            end.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = cs.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(se.title, style = MaterialTheme.typography.titleMedium)
                Text(whenText, style = MaterialTheme.typography.bodyMedium, color = cs.primary)
                se.location?.let {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(R.drawable.ic_location, tint = cs.onSurfaceVariant, size = 16.dp)
                        Spacer(Modifier.width(4.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
            when {
                inApp -> Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(R.drawable.ic_check, tint = cs.primary, size = 18.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("В приложении", style = MaterialTheme.typography.labelMedium, color = cs.primary)
                }
                se.allDay -> {}
                else -> TextButton(onClick = onImport) { Text("Добавить") }
            }
        }
    }
}
