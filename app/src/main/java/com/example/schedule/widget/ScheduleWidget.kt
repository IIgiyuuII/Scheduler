package com.example.schedule.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.schedule.R
import com.example.schedule.data.AppDb
import com.example.schedule.data.EventEntity
import com.example.schedule.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Действие «открыть приложение». С eventId приложение откроет это событие (extra «eventId»).
 * Uri в data делает Intent уникальным для каждого события, иначе система склеила бы их
 * в один PendingIntent (extras при сравнении Intent не учитываются).
 */
private fun openApp(eventId: Long? = null): Action {
    val intent = Intent()
        .setComponent(ComponentName("com.example.schedule", MainActivity::class.java.name))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (eventId != null) {
        intent.putExtra("eventId", eventId)
        intent.data = Uri.parse("schedule://event/$eventId")
    }
    return actionStartActivity(intent)
}

private val BG = ColorProvider(day = Color(0xFFF6F5FF), night = Color(0xFF181730))
private val TEXT = ColorProvider(day = Color(0xFF1B1A2E), night = Color(0xFFE8E6FA))
private val TEXT_SOFT = ColorProvider(day = Color(0xFF4B4A63), night = Color(0xFFC9C7E3))
private val ACCENT = ColorProvider(day = Color(0xFF4F46E5), night = Color(0xFFA5B4FC))

class ScheduleWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Данные читаем из Room вне главного потока, при каждой перерисовке
        val events = withContext(Dispatchers.IO) {
            AppDb.get(context).eventDao().upcoming(System.currentTimeMillis(), 8)
        }
        provideContent { Content(events) }
    }
}

@Composable
private fun Content(events: List<EventEntity>) {
    Column(
        GlanceModifier.fillMaxSize().background(BG).cornerRadius(22.dp).padding(12.dp)
    ) {
        Row(
            GlanceModifier.fillMaxWidth().clickable(openApp()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(ImageProvider(R.drawable.ic_logo), null, GlanceModifier.size(22.dp))
            Spacer(GlanceModifier.width(8.dp))
            Text(
                "Ближайшие события",
                style = TextStyle(color = TEXT, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
        }
        Spacer(GlanceModifier.height(6.dp))
        if (events.isEmpty()) {
            Text("Событий пока нет", style = TextStyle(color = TEXT_SOFT, fontSize = 13.sp))
        } else {
            LazyColumn(GlanceModifier.fillMaxWidth()) {
                items(events, itemId = { it.id }) { e -> EventRow(e) }
            }
        }
    }
}

@Composable
private fun EventRow(e: EventEntity) {
    val start = Instant.ofEpochMilli(e.startUtcMillis).atZone(ZoneId.systemDefault())
    Row(
        GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            // По нажатию открываем приложение с этим событием
            .clickable(openApp(e.id)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(GlanceModifier.width(70.dp)) {
            Text(
                start.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = TextStyle(color = ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            )
            Text(dayLabel(start.toLocalDate()), style = TextStyle(color = TEXT_SOFT, fontSize = 11.sp), maxLines = 1)
        }
        Column(GlanceModifier.defaultWeight()) {
            Text(e.title, style = TextStyle(color = TEXT, fontSize = 14.sp), maxLines = 1)
            e.location?.let { Text(it, style = TextStyle(color = TEXT_SOFT, fontSize = 11.sp), maxLines = 1) }
        }
    }
}

private fun dayLabel(d: LocalDate): String {
    val today = LocalDate.now()
    return when (d) {
        today -> "Сегодня"
        today.plusDays(1) -> "Завтра"
        else -> d.format(DateTimeFormatter.ofPattern("EEE, d MMM", Locale("ru")))
    }
}

class ScheduleWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ScheduleWidget()

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // Пока на экране есть виджет — держим будильник на полночь; без виджетов снимаем
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_DISABLED) {
            WidgetUpdater.cancelMidnight(context)
        } else {
            WidgetUpdater.scheduleMidnight(context)
        }
    }
}
