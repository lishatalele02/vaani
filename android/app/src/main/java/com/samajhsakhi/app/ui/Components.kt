@file:OptIn(ExperimentalMaterial3Api::class)

package com.samajhsakhi.app.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.samajhsakhi.app.audio.PlayState
import com.samajhsakhi.app.data.Lang
import com.samajhsakhi.app.data.formatDuration

val LocalSnack = compositionLocalOf { SnackbarHostState() }

/** Standard screen: flat top bar with optional back arrow, then scrolling or custom content. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    scroll: Boolean = true,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(LocalSnack.current) },
    ) { pad ->
        val base = Modifier.fillMaxSize().padding(pad)
        content(if (scroll) base.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp) else base)
    }
}

@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    outlined: Boolean = false,
) {
    val shape = RoundedCornerShape(14.dp)
    if (outlined) {
        OutlinedButton(
            onClick = onClick, enabled = enabled, shape = shape,
            modifier = modifier.fillMaxWidth().height(54.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
    } else {
        Button(
            onClick = onClick, enabled = enabled, shape = shape,
            modifier = modifier.fillMaxWidth().height(54.dp),
        ) { Text(text, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(top = 28.dp, bottom = 8.dp).semantics { heading() },
    )
}

/** Flat card with a hairline border. */
@Composable
fun InfoCard(
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surface,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun ErrorBanner(message: String?, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    if (message.isNullOrBlank()) return
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message, color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
            if (onRetry != null) TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun LangChips(options: List<Lang>, selected: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { l ->
            FilterChip(
                selected = selected == l.code,
                onClick = { onSelect(l.code) },
                label = { Text(l.native, fontSize = 15.sp) },
            )
        }
    }
}

/** Dropdown: a button showing the current value and a menu of options. */
@Composable
fun <T> DropdownField(
    label: String,
    current: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Text(current, modifier = Modifier.weight(1f), textAlign = TextAlign.Start)
                Text("v", color = MaterialTheme.colorScheme.outline)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(optionLabel(o)) }, onClick = { open = false; onSelect(o) })
                }
            }
        }
    }
}

@Composable
fun LabeledField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        modifier = modifier.fillMaxWidth(), singleLine = singleLine, minLines = minLines,
        shape = RoundedCornerShape(12.dp),
    )
}

/** A plain list row with a chevron, used for simple navigation. */
@Composable
fun ListRow(title: String, subtitle: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(">", fontSize = 18.sp, color = MaterialTheme.colorScheme.outline)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** Simple microphone drawn with shapes (no emoji, no icon library needed). */
@Composable
fun MicGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val bodyW = w * 0.32f
        drawRoundRect(
            color = color, topLeft = Offset((w - bodyW) / 2f, h * 0.08f),
            size = Size(bodyW, h * 0.50f), cornerRadius = CornerRadius(bodyW / 2f, bodyW / 2f),
        )
        val sw = w * 0.07f
        drawArc(
            color = color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.20f), size = Size(w * 0.56f, h * 0.50f),
            style = Stroke(width = sw, cap = StrokeCap.Round),
        )
        drawLine(color, Offset(w / 2f, h * 0.70f), Offset(w / 2f, h * 0.90f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.36f, h * 0.90f), Offset(w * 0.64f, h * 0.90f), sw, StrokeCap.Round)
    }
}

@Composable
fun StopGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        drawRoundRect(
            color = color, topLeft = Offset(w * 0.22f, w * 0.22f),
            size = Size(w * 0.56f, w * 0.56f), cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
        )
    }
}

/** The one big round button used to start and stop recording. */
@Composable
fun RecordButton(recording: Boolean, level: Float, onClick: () -> Unit, size: Dp = 168.dp) {
    val pulse by animateDpAsState(targetValue = (level * 220f).coerceIn(0f, 44f).dp, label = "pulse")
    val color = if (recording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val haptic = LocalHapticFeedback.current
    Box(Modifier.size(size + 48.dp), contentAlignment = Alignment.Center) {
        if (recording) {
            Box(Modifier.size(size + pulse).clip(CircleShape).background(color.copy(alpha = 0.16f)))
        }
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(color)
                .semantics { contentDescription = if (recording) "Stop recording" else "Start recording" }
                // haptic tick on both record and stop, so the state change can be felt as well as seen
                .clickable(role = Role.Button, onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }),
            contentAlignment = Alignment.Center,
        ) {
            if (recording) StopGlyph(Color.White, Modifier.size(size * 0.42f))
            else MicGlyph(Color.White, Modifier.size(size * 0.42f))
        }
    }
}

/** Playback controls for the generated speech. */
@Composable
fun PlaybackControls(vm: AppViewModel, modifier: Modifier = Modifier) {
    val state by vm.speechPlayer.state.collectAsStateWithLifecycle()
    val pos by vm.speechPlayer.positionMs.collectAsStateWithLifecycle()
    val dur by vm.speechPlayer.durationMs.collectAsStateWithLifecycle()
    val loading by vm.speechLoading.collectAsStateWithLifecycle()

    Column(modifier.fillMaxWidth()) {
        if (loading) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Preparing voice...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }
        if (state == PlayState.Error) {
            Text("The audio could not be played. Please try again.", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        if (state == PlayState.Idle && dur == 0 && state != PlayState.Error) return@Column
        LinearProgressIndicator(
            progress = { if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(4.dp),
        )
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatDuration(pos.toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(dur.toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (state) {
                PlayState.Playing -> OutlinedButton(onClick = { vm.speechPlayer.pause() }, modifier = Modifier.weight(1f)) { Text("Pause") }
                PlayState.Paused -> OutlinedButton(onClick = { vm.speechPlayer.resume() }, modifier = Modifier.weight(1f)) { Text("Resume") }
                else -> OutlinedButton(onClick = { vm.speechPlayer.replay() }, modifier = Modifier.weight(1f)) { Text("Replay") }
            }
            OutlinedButton(
                onClick = { vm.speechPlayer.stop() },
                enabled = state == PlayState.Playing || state == PlayState.Paused,
                modifier = Modifier.weight(1f),
            ) { Text("Stop") }
        }
    }
}

/** Scrollable column modifier for dialog bodies. */
@Composable
fun Modifier.verticalScrollCompat(): Modifier = this.verticalScroll(rememberScrollState())
