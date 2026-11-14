package com.example.schedule.ui

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.schedule.R
import java.time.ZoneId
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private val vm: EventViewModel by viewModels()

    // id события из виджета; -1 — открывать ничего не нужно
    private var openEventId by mutableLongStateOf(-1L)

    // Часовой пояс устройства: при смене пересобираем интерфейс, чтобы время показалось в новом поясе
    private var deviceZoneId by mutableStateOf(ZoneId.systemDefault().id)
    private val zoneReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refreshZone()
    }

    private fun refreshZone() {
        TimeZone.setDefault(null)   // сбросить кэш JVM и взять текущий пояс системы
        deviceZoneId = ZoneId.systemDefault().id
    }

    // Разрешения календаря
    private var calendarGranted by mutableStateOf(false)
    private var calendarDenied by mutableStateOf(false)   // отказ «навсегда»: диалог больше не покажут
    private val calendarPermissions =
        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    private val calendarLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            refreshCalendarAccess(afterRequest = true)
        }

    private fun hasCalendarPermission() = calendarPermissions.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun refreshCalendarAccess(afterRequest: Boolean = false) {
        calendarGranted = hasCalendarPermission()
        if (calendarGranted) {
            calendarDenied = false
        } else if (afterRequest) {
            // Если система больше не показывает диалог, остаётся только экран настроек
            calendarDenied = calendarPermissions.none { shouldShowRequestPermissionRationale(it) }
        }
    }

    private fun openAppSettings() {
        startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
                .launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Только при первом запуске, чтобы после поворота экрана событие не открывалось снова
        if (savedInstanceState == null) openEventId = intent.getLongExtra("eventId", -1L)

        setContent {
            ScheduleTheme {
                key(deviceZoneId) { App(
                    vm = vm,
                    openEventId = openEventId,
                    onOpenHandled = { openEventId = -1L },
                    access = CalendarAccess(
                        granted = calendarGranted,
                        permanentlyDenied = calendarDenied,
                        onRequest = { calendarLauncher.launch(calendarPermissions) },
                        onOpenSettings = { openAppSettings() },
                    ),
                ) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refreshZone()
        ContextCompat.registerReceiver(
            this, zoneReceiver, IntentFilter(Intent.ACTION_TIMEZONE_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(zoneReceiver)
    }

    override fun onResume() {
        super.onResume()
        // Пользователь мог выдать или отозвать разрешение в настройках
        refreshCalendarAccess()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openEventId = intent.getLongExtra("eventId", -1L)
    }
}

@Composable
private fun App(vm: EventViewModel, openEventId: Long, onOpenHandled: () -> Unit, access: CalendarAccess) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(openEventId) { if (openEventId >= 0) tab = 1 }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { AppIcon(R.drawable.ic_add) }, label = { Text("Добавить") },
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { AppIcon(R.drawable.ic_calendar) }, label = { Text("Повестка") },
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { AppIcon(R.drawable.ic_sync) }, label = { Text("Календарь") },
                )
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            AppHeader(
                title = "Помощник по расписанию",
                subtitle = when (tab) {
                    0 -> "Напишите событие обычной фразой"
                    1 -> "Ваши события и напоминания"
                    else -> "События из календаря телефона"
                },
            )
            when (tab) {
                0 -> AddScreen(vm, access, Modifier.weight(1f))
                1 -> AgendaScreen(vm, openEventId, onOpenHandled, Modifier.weight(1f))
                else -> CalendarScreen(vm, access, Modifier.weight(1f))
            }
        }
    }
}
