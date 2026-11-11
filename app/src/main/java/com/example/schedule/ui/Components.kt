package com.example.schedule.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.schedule.R

@Composable
fun AppIcon(
    @DrawableRes id: Int,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    size: Dp = 24.dp,
) {
    Icon(painterResource(id), contentDescription = null, modifier = modifier.size(size), tint = tint)
}

/** Градиентная шапка с логотипом. */
@Composable
fun AppHeader(title: String, subtitle: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Brand1, Brand2)),
                RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
            )
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun AppField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    @DrawableRes icon: Int?,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        leadingIcon = if (icon != null) ({ AppIcon(icon) }) else null,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
    )
}

/** Плашка-сообщение: ошибка/предупреждение или подсказка. */
@Composable
fun Banner(text: String, isError: Boolean) {
    val cs = MaterialTheme.colorScheme
    val bg = if (isError) cs.errorContainer else cs.secondaryContainer
    val fg = if (isError) cs.onErrorContainer else cs.onSecondaryContainer
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(if (isError) R.drawable.ic_warning else R.drawable.ic_sparkle, tint = fg, size = 20.dp)
        Spacer(Modifier.width(10.dp))
        Text(text, color = fg, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Чип-подсказка с примером фразы. */
@Composable
fun SuggestionPill(text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(cs.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = cs.onPrimaryContainer, style = MaterialTheme.typography.labelLarge)
    }
}

/** Переключатель «День / Неделя». */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.surfaceVariant)
            .padding(4.dp)
    ) {
        options.forEachIndexed { i, text ->
            val on = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (on) cs.primary else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text,
                    color = if (on) cs.onPrimary else cs.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
