package com.example.schedule.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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

class MainActivity : ComponentActivity() {
    private val vm: EventViewModel by viewModels()

    // id события из виджета; -1 — открывать ничего не нужно
    private var openEventId by mutableLongStateOf(-1L)

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
            ScheduleTheme { App(vm, openEventId) { openEventId = -1L } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openEventId = intent.getLongExtra("eventId", -1L)
    }
}

@Composable
private fun App(vm: EventViewModel, openEventId: Long, onOpenHandled: () -> Unit) {
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
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            AppHeader(
                title = "Помощник по расписанию",
                subtitle = if (tab == 0) "Напишите событие обычной фразой" else "Ваши события и напоминания",
            )
            if (tab == 0) AddScreen(vm, Modifier.weight(1f)) else AgendaScreen(vm, openEventId, onOpenHandled, Modifier.weight(1f))
        }
    }
}
