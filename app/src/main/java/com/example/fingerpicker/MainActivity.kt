package com.example.fingerpicker

import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextAlign

private val PALETTE = listOf(
    Color(0xFFE53935), Color(0xFF1E88E5), Color(0xFF43A047), Color(0xFFFDD835),
    Color(0xFF8E24AA), Color(0xFFFB8C00), Color(0xFF00ACC1), Color(0xFFD81B60),
    Color(0xFF6D4C41), Color(0xFF7CB342)
)

enum class Mode(val title: String) {
    PICK("Choose one person"),
    SPLIT("Split into groups"),
    NUMBER("Set an order")
}

/** Holds live fingers, and the frozen result once the countdown finishes. */
class TouchState {
    val fingers = mutableStateMapOf<Long, Offset>()   // fingers currently down
    val frozen = mutableStateMapOf<Long, Offset>()    // positions shown in the result
    var results by mutableStateOf<Map<Long, Int>?>(null)
    var countdown by mutableIntStateOf(0)
    private val colors = HashMap<Long, Color>()
    private var next = 0

    fun colorOf(id: Long): Color = colors[id] ?: Color.White

    fun update(points: Map<Long, Offset>) {
        // A new touch after a finished round (all fingers were up) starts a fresh round.
        if (results != null && fingers.isEmpty() && points.isNotEmpty()) {
            results = null; frozen.clear(); colors.clear(); next = 0
        }
        fingers.keys.toList().forEach { if (it !in points) fingers.remove(it) }
        if (points.isEmpty() && results == null) { colors.clear(); next = 0 }
        for ((id, pos) in points) {
            if (id !in colors) colors[id] = PALETTE[next++ % PALETTE.size]
            fingers[id] = pos
            if (results?.containsKey(id) == true) frozen[id] = pos
        }
    }

    fun lock(mode: Mode, groups: Int) {
        val ids = fingers.keys.toList().shuffled()
        frozen.clear()
        frozen.putAll(fingers)
        results = when (mode) {
            Mode.PICK -> ids.associateWith { if (it == ids.first()) 1 else 0 }
            Mode.SPLIT -> ids.mapIndexed { i, id -> id to (i % groups) }.toMap()
            Mode.NUMBER -> ids.mapIndexed { i, id -> id to (i + 1) }.toMap()
        }
        countdown = 0
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize()) { App() }
            }
        }
    }
}

@Composable
fun App() {
    var modeIdx by rememberSaveable { mutableIntStateOf(0) }
    var groups by rememberSaveable { mutableIntStateOf(2) }
    var running by rememberSaveable { mutableStateOf(false) }
    if (running) {
        TouchScreen(Mode.values()[modeIdx], groups) { running = false }
    } else {
        SettingsScreen(modeIdx, groups, { modeIdx = it }, { groups = it }) { running = true }
    }
}

@Composable
fun SettingsScreen(
    modeIdx: Int, groups: Int,
    onMode: (Int) -> Unit, onGroups: (Int) -> Unit, onStart: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Pick Me!", style = MaterialTheme.typography.headlineMedium)
        Text("Everyone puts one finger on the screen. \nPick what should happen:")
        Mode.values().forEachIndexed { i, m ->
            Row(
                Modifier.fillMaxWidth()
                    .selectable(selected = i == modeIdx, onClick = { onMode(i) }, role = Role.RadioButton)
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(selected = i == modeIdx, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(m.title, fontSize = 18.sp)
            }
        }
        if (Mode.values()[modeIdx] == Mode.SPLIT) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Number of groups", fontSize = 18.sp, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = { if (groups > 2) onGroups(groups - 1) }) { Text("−") }
                Text("$groups", fontSize = 20.sp, modifier = Modifier.padding(horizontal = 16.dp))
                OutlinedButton(onClick = { if (groups < PALETTE.size) onGroups(groups + 1) }) { Text("+") }
            }
        }
        Spacer(Modifier.weight(1f))
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Start", fontSize = 18.sp)
        }
        Text(
            "created by Alon Suissa",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun TouchScreen(mode: Mode, groups: Int, onExit: () -> Unit) {
    val state = remember { TouchState() }
    val haptic = LocalHapticFeedback.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    BackHandler(onBack = onExit)

    val minFingers = if (mode == Mode.SPLIT) maxOf(2, groups) else 2
    val ids = state.fingers.keys.toSet()
    val locked = state.results != null

    // Restarts whenever a finger is added/removed; runs the action after a 3 s countdown.
    LaunchedEffect(ids, locked) {
        if (locked || ids.size < minFingers) {
            state.countdown = 0
            return@LaunchedEffect
        }
        for (s in 3 downTo 1) { state.countdown = s; delay(1000) }
        state.lock(mode, groups)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val paint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            color = android.graphics.Color.WHITE
            typeface = Typeface.DEFAULT_BOLD
        }
    }
    val res = state.results
    val status = when {
        res == null && ids.size < minFingers -> "Put at least $minFingers fingers on the screen"
        res == null -> "Hold still… ${state.countdown}"
        mode == Mode.PICK -> "Chosen! Lift all fingers and touch again to restart"
        mode == Mode.SPLIT -> res.values.groupingBy { it }.eachCount().toSortedMap().entries
            .joinToString("  ·  ") { "G${it.key + 1}: ${it.value}" }
        else -> "Numbered 1–${res.size}. Touch again to restart"
    }

    Column(Modifier.fillMaxSize().background(Color(0xFF111111)).systemBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onExit) { Text("← home") }
            Spacer(Modifier.width(8.dp))
            Text(status, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f))
        }
        Canvas(
            Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pts = event.changes.filter { it.pressed }
                            .associate { it.id.value to it.position }
                        state.update(pts)
                    }
                }
            }
        ) {
            val r = 52.dp.toPx()
            val result = state.results
            if (result == null) {
                for ((id, pos) in state.fingers) {
                    val c = state.colorOf(id)
                    drawCircle(c.copy(alpha = 0.25f), r * 1.5f, pos)
                    drawCircle(c, r, pos)
                }
            } else {
                for ((id, pos) in state.frozen) {
                    val v = result[id] ?: continue
                    when (mode) {
                        Mode.PICK ->
                            if (v == 1) {
                                drawCircle(Color.White, r * 2.3f, pos, style = Stroke(6.dp.toPx()))
                                drawCircle(state.colorOf(id), r * 1.9f, pos)
                            } else {
                                drawCircle(Color.Gray.copy(alpha = 0.3f), r * 0.7f, pos)
                            }
                        Mode.SPLIT -> {
                            drawCircle(PALETTE[v % PALETTE.size], r, pos)
                            label("${v + 1}", pos, r * 0.9f, paint)
                        }
                        Mode.NUMBER -> {
                            drawCircle(state.colorOf(id), r, pos)
                            label("$v", pos, r * 0.9f, paint)
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.label(text: String, pos: Offset, size: Float, paint: Paint) {
    paint.textSize = size
    drawContext.canvas.nativeCanvas.drawText(text, pos.x, pos.y + size * 0.35f, paint)
}
