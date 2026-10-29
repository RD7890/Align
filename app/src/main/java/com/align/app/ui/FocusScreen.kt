package com.align.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.align.app.R
import com.align.app.data.PomoState
import com.align.app.service.PomodoroEngine

private fun clock(ms: Long): String {
    val total = ((ms + 999L) / 1000L).coerceAtLeast(0L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}

private fun formatTotal(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

/**
 * Mode selector: two quiet text tabs with a small pill that glides under the active one (no buttons, no
 * background). Locked while a session runs. The clock area below can also be swiped to switch.
 */
@Composable
private fun ModeTabs(mode: Int, enabled: Boolean, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    val tabWidth = 124.dp
    val glide by animateFloatAsState(
        mode.toFloat(), tween(500, easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)), label = "modeGlide"
    )
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(Modifier.graphicsLayer { alpha = if (enabled) 1f else 0.55f }) {
            Row {
                listOf("Timer", "Pomodoro").forEachIndexed { i, label ->
                    val color by animateColorAsState(
                        if (mode == i) FG.Ink else FG.TextSecondary, tween(300), label = "tabColor"
                    )
                    Box(
                        Modifier
                            .width(tabWidth)
                            .height(40.dp)
                            .pressable(enabled = enabled && mode != i, onClick = { onChange(i) }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont, color = color)
                    }
                }
            }
            Box(
                Modifier
                    .offset(x = tabWidth * glide + (tabWidth - 30.dp) / 2)
                    .size(width = 30.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(FG.Primary)
            )
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont, modifier = Modifier.weight(1f))
            FgRoundButton(onClick = onMinus, size = 34.dp) {
                Icon(painterResource(R.drawable.ph_minus_bold), null, tint = FG.OnStrong, modifier = Modifier.size(16.dp))
            }
            Box(Modifier.width(72.dp), contentAlignment = Alignment.Center) {
                AnimatedNumber(value, fontSize = 16.sp, color = FG.Primary)
            }
            FgRoundButton(onClick = onPlus, size = 34.dp) {
                Icon(painterResource(R.drawable.ph_plus_bold), null, tint = FG.OnStrong, modifier = Modifier.size(16.dp))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(FG.Track))
    }
}

/** Home tab: focus timer, duration, start / hold-to-end and the blocked-apps summary. */
fun LazyListScope.focusItems(
    running: Boolean,
    remainingMs: () -> Long,
    durationMin: Int,
    onDurationChange: (Int) -> Unit,
    blocked: Set<String>,
    completed: Boolean,
    permsOk: Boolean,
    onStart: () -> Unit,
    onEnd: () -> Unit,
    onEditBlocked: () -> Unit,
    onSetup: () -> Unit,
    completedNote: String? = null,
    mode: Int = 0,
    onModeChange: (Int) -> Unit = {},
    pomo: PomoState = PomoState(),
    nowMs: () -> Long = { 0L },
    onPomoChange: (PomoState) -> Unit = {},
    onPomoStart: () -> Unit = {},
    onPomoNext: () -> Unit = {}
) {
    val totalMs = durationMin * 60_000L
    val pomodoro = mode == 1
    val phase = pomo.phase
    val phaseRunning = phase != PomodoroEngine.IDLE && pomo.end != 0L
    val waiting = phase != PomodoroEngine.IDLE && pomo.end == 0L
    val onBreak = phase == PomodoroEngine.SHORT_BREAK || phase == PomodoroEngine.LONG_BREAK
    val phaseTotalMs = if (phase == PomodoroEngine.IDLE) 1L else PomodoroEngine.lengthMs(pomo, phase)
    val sessionActive = if (pomodoro) phase != PomodoroEngine.IDLE else running

    item(key = "mode") {
        ModeTabs(mode, enabled = !sessionActive, onChange = onModeChange, modifier = Modifier.staggerIn(0))
    }

    if (completed && !running) {
        item(key = "done") {
            Spacer(Modifier.height(8.dp))
            SectionCard(Modifier.staggerIn(0)) {
                Illustration("success", Modifier.fillMaxWidth().height(150.dp))
                Spacer(Modifier.height(6.dp))
                Text(
                    if (pomodoro) "Pomodoro complete!" else "Session complete!",
                    fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    completedNote ?: "Nice work. Your blocked apps are unlocked again.",
                    fontSize = 13.sp, color = FG.TextSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }

    if (!permsOk) {
        item(key = "perms") {
            Spacer(Modifier.height(12.dp))
            SectionCard(Modifier.staggerIn(0)) {
                Text("Allow permissions to block apps", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                Text(
                    "Usage access and \"Display over other apps\" are needed so Align can stop blocked apps.",
                    fontSize = 12.sp, color = FG.TextSecondary
                )
                Spacer(Modifier.height(12.dp))
                FgButton("Open setup", onSetup, Modifier.fillMaxWidth(), height = 46.dp)
            }
        }
    }

    item(key = "timer") {
        // The clock is the only part that ticks: it reads the time providers here, inside its own item scope,
        // so the rest of the Home tab is not recomposed every 250 ms.
        val now = nowMs()
        val remaining = remainingMs()
        val phaseLeftMs = if (phaseRunning) (pomo.end - now).coerceIn(0L, phaseTotalMs) else phaseTotalMs
        val phaseElapsed = if (phaseRunning) 1f - phaseLeftMs.toFloat() / phaseTotalMs else 0f
        val timerElapsed = if (running) 1f - remaining.toFloat() / totalMs else 0f

        // 0 -> 1 while switching Timer -> Pomodoro. One ring stays put; the round dots pop out of it and glide
        // under the time (and back into the ring on the way back).
        val morph = animateFloatAsState(
            if (pomodoro) 1f else 0f, tween(900, easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)), label = "modeMorph"
        )
        val dotStates = List(pomo.rounds) { i ->
            val r = i + 1
            when {
                phase != PomodoroEngine.IDLE && (r < pomo.round || (r == pomo.round && phase != PomodoroEngine.FOCUS)) -> 2
                phase == PomodoroEngine.FOCUS && r == pomo.round -> 1
                else -> 0
            }
        }
        val timeText = when {
            pomodoro -> if (phase == PomodoroEngine.IDLE) clock(pomo.focusMin * 60_000L) else clock(phaseLeftMs)
            running -> clock(remaining)
            else -> clock(totalMs)
        }
        val label = when {
            !pomodoro -> if (running) "stay focused" else "minutes of focus"
            phase == PomodoroEngine.IDLE -> "${pomo.rounds} rounds"
            phase == PomodoroEngine.FOCUS -> "round ${pomo.round} of ${pomo.rounds}"
            phase == PomodoroEngine.SHORT_BREAK -> "short break"
            else -> "long break"
        }
        Spacer(Modifier.height(14.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .staggerIn(1)
                .pointerInput(sessionActive, mode) {
                    // Swipe the clock sideways to switch mode (left = Pomodoro, right = Timer).
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = { dx = 0f },
                        onDragEnd = {
                            if (!sessionActive) {
                                if (dx < -80f && mode == 0) onModeChange(1)
                                else if (dx > 80f && mode == 1) onModeChange(0)
                            }
                        },
                        onHorizontalDrag = { _, delta -> dx += delta }
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                FocusRing(
                    fraction = if (pomodoro) phaseElapsed else timerElapsed,
                    color = if (pomodoro && onBreak) FG.Green else FG.Primary,
                    modifier = Modifier.fillMaxSize(),
                    dots = RingDots(dotStates) { morph.value }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AnimatedNumber(timeText, fontSize = 54.sp)
                        Crossfade(
                            targetState = label to (pomodoro && onBreak),
                            animationSpec = tween(300),
                            label = "dialLabel"
                        ) { (text, green) ->
                            Text(text, fontSize = 13.sp, color = if (green) FG.Green else FG.TextSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            // Status chip: swaps text + colour with a cross-fade for every state of Timer and Pomodoro.
            val status: Pair<String, Color> = when {
                !pomodoro -> if (running) "FOCUS MODE ON" to FG.Primary else "READY" to FG.Strong
                phase == PomodoroEngine.IDLE -> "READY" to FG.Strong
                waiting -> "UP NEXT" to FG.Strong
                onBreak -> "BREAK" to FG.Green
                else -> "FOCUS ON" to FG.Primary
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Crossfade(targetState = status, animationSpec = tween(350), label = "statusChip") { (text, color) ->
                    Chip(text, color, if (color == FG.Strong) FG.OnStrong else FG.White)
                }
                Chip("${blocked.size} apps blocked", FG.Strong, FG.OnStrong)
            }
        }
    }

    item(key = "controls") {
        Spacer(Modifier.height(22.dp))
        // No card: the controls sit directly on the page. The box animates its height when the mode changes.
        Box(Modifier.fillMaxWidth().staggerIn(2).animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))) {
            Crossfade(targetState = pomodoro, animationSpec = tween(350), label = "controlsMode") { isPomo ->
            Column(Modifier.fillMaxWidth()) {
            if (!isPomo) {
                if (running) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ph_lock_simple_fill), null, tint = FG.Primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Locked until the timer ends", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                            Text("Settings and blocked apps can't be changed.", fontSize = 12.sp, color = FG.TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HoldButton("Hold to end session", onEnd, Modifier.fillMaxWidth())
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Duration", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont, modifier = Modifier.weight(1f))
                        AnimatedNumber("$durationMin min", fontSize = 22.sp, color = FG.Primary)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FgRoundButton(onClick = { onDurationChange((durationMin - 5).coerceAtLeast(5)) }, size = 40.dp) {
                            Icon(painterResource(R.drawable.ph_minus_bold), null, tint = FG.OnStrong, modifier = Modifier.size(18.dp))
                        }
                        FgSlider(
                            value = durationMin,
                            onValueChange = onDurationChange,
                            min = 5, max = 180, step = 5,
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        )
                        FgRoundButton(onClick = { onDurationChange((durationMin + 5).coerceAtMost(180)) }, size = 40.dp) {
                            Icon(painterResource(R.drawable.ph_plus_bold), null, tint = FG.OnStrong, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    FgButton("Start focus", onStart, Modifier.fillMaxWidth(), height = 58.dp)
                }
            } else when {
                // Focus round running: locked, only a long press ends the whole session.
                phase == PomodoroEngine.FOCUS && phaseRunning -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ph_lock_simple_fill), null, tint = FG.Primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("Locked until this round ends", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                            Text("Settings and blocked apps can't be changed.", fontSize = 12.sp, color = FG.TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    HoldButton("Hold to end session", onEnd, Modifier.fillMaxWidth())
                }
                // Break running: apps are free, the break can be skipped.
                onBreak && phaseRunning -> {
                    Column {
                        Text("Take a break", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                        Text(
                            if (phase == PomodoroEngine.LONG_BREAK) "Apps are unlocked. This is the last break of the cycle."
                            else "Apps are unlocked. Next up: focus round ${pomo.round + 1}.",
                            fontSize = 12.sp, color = FG.TextSecondary
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FgButton(
                            if (phase == PomodoroEngine.LONG_BREAK) "Finish now" else "Skip break",
                            onPomoNext, Modifier.weight(1f), style = FgButtonStyle.Light, height = 52.dp
                        )
                        FgButton("End session", onEnd, Modifier.weight(1f), style = FgButtonStyle.Dark, height = 52.dp)
                    }
                }
                // Manual continue: the next phase waits for a tap.
                waiting -> {
                    Column {
                        Text(
                            if (phase == PomodoroEngine.FOCUS) "Focus round ${pomo.round} is ready" else "Your break is ready",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont
                        )
                        Text("Apps stay unlocked until you start.", fontSize = 12.sp, color = FG.TextSecondary)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FgButton(
                            if (phase == PomodoroEngine.FOCUS) "Start focus" else "Start break",
                            onPomoNext, Modifier.weight(1f), height = 52.dp
                        )
                        FgButton("End session", onEnd, Modifier.weight(1f), style = FgButtonStyle.Light, height = 52.dp)
                    }
                }
                // Idle: settings + start.
                else -> {
                    StepperRow(
                        "Focus", "${pomo.focusMin} min",
                        { onPomoChange(pomo.copy(focusMin = (pomo.focusMin - 5).coerceAtLeast(5))) },
                        { onPomoChange(pomo.copy(focusMin = (pomo.focusMin + 5).coerceAtMost(90))) }
                    )
                    StepperRow(
                        "Short break", "${pomo.breakMin} min",
                        { onPomoChange(pomo.copy(breakMin = (pomo.breakMin - 1).coerceAtLeast(1))) },
                        { onPomoChange(pomo.copy(breakMin = (pomo.breakMin + 1).coerceAtMost(30))) }
                    )
                    StepperRow(
                        "Long break", "${pomo.longMin} min",
                        { onPomoChange(pomo.copy(longMin = (pomo.longMin - 5).coerceAtLeast(5))) },
                        { onPomoChange(pomo.copy(longMin = (pomo.longMin + 5).coerceAtMost(45))) }
                    )
                    StepperRow(
                        "Rounds", "${pomo.rounds}",
                        { onPomoChange(pomo.copy(rounds = (pomo.rounds - 1).coerceAtLeast(1))) },
                        { onPomoChange(pomo.copy(rounds = (pomo.rounds + 1).coerceAtMost(8))) }
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Auto-continue", fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                            Text("Start the next phase on its own", fontSize = 12.sp, color = FG.TextSecondary)
                        }
                        Switch(
                            checked = pomo.auto,
                            onCheckedChange = { onPomoChange(pomo.copy(auto = it)) },
                            colors = fgSwitchColors()
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val cycleMin = pomo.rounds * pomo.focusMin + (pomo.rounds - 1) * pomo.breakMin + pomo.longMin
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Whole cycle: about ", fontSize = 12.sp, color = FG.TextSecondary)
                        AnimatedNumber(
                            formatTotal(cycleMin), fontSize = 12.sp, color = FG.TextSecondary,
                            fontWeight = FontWeight.Normal, fontFamily = null
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    FgButton("Start pomodoro", onPomoStart, Modifier.fillMaxWidth(), height = 58.dp)
                }
            }
            }
            }
        }
    }

    item(key = "blocked") {
        Spacer(Modifier.height(12.dp))
        SectionCard(Modifier.staggerIn(3)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Blocked during focus", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                    Text(
                        if (blocked.isEmpty()) "No apps selected" else "${blocked.size} apps - you choose which",
                        fontSize = 12.sp, color = FG.TextSecondary
                    )
                }
                FgButton("Edit", onEditBlocked, style = FgButtonStyle.Light, height = 42.dp, enabled = !running)
            }
            if (blocked.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    blocked.sorted().take(7).forEach { pkg -> AppIconImage(pkg, 32.dp) }
                }
            }
        }
    }
}
