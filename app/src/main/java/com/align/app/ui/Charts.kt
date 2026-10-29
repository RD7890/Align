package com.align.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** Animated donut. [values] and [colors] line up by index. */
@Composable
fun DonutChart(
    values: List<Float>,
    colors: List<Color>,
    centerTitle: String,
    centerSub: String,
    modifier: Modifier = Modifier
) {
    val total = values.sum()
    val hasData = total > 0f
    // Sweep plays when data first appears (empty -> filled), not on every refresh of the numbers.
    val progress = remember { Animatable(if (ChartIntro.played && hasData) 1f else 0f) }
    LaunchedEffect(hasData) {
        if (hasData) {
            if (progress.value < 1f) {
                delay(250) // let the tab slide finish before animating
                progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                ChartIntro.played = true
            }
        } else progress.snapTo(0f)
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.15f
            val diameter = size.minDimension - stroke
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(
                color = FG.Track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            if (hasData) {
                val gap = if (values.size > 1) 3f else 0f
                var start = -90f
                values.forEachIndexed { i, v ->
                    val sweep = v / total * 360f * progress.value
                    val drawn = (sweep - gap).coerceAtLeast(0.01f)
                    if (sweep > 0.05f) {
                        drawArc(
                            color = colors[i % colors.size],
                            startAngle = start + gap / 2f,
                            sweepAngle = drawn,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke)
                        )
                    }
                    start += sweep
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTitle, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont, color = FG.Ink)
            Text(centerSub, fontSize = 12.sp, color = FG.TextSecondary)
        }
    }
}

/** Circular progress ring with centred content. */
@Composable
fun RingProgress(
    fraction: Float,
    modifier: Modifier = Modifier,
    trackColor: Color = FG.Track,
    ringBrush: Brush = Brush.linearGradient(listOf(FG.PrimaryLight, FG.Primary)),
    strokeWidth: Dp = 8.dp,
    content: @Composable () -> Unit = {}
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(fraction) {
        progress.animateTo(fraction.coerceIn(0f, 1f), tween(900, easing = FastOutSlowInEasing))
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            if (progress.value > 0.001f) {
                drawArc(
                    brush = ringBrush,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.value,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
        }
        content()
    }
}

/** Chart entrance animations play on the first visit only; later tab visits draw the final state at once. */
object ChartIntro {
    @Volatile
    var played = false
}

/**
 * Round dots that live on the ring. [morph] 0 = hidden inside the ring, 1 = lined up under the time. Moving
 * between the two is the Timer <-> Pomodoro transition: the dots pop out of the ring one after another and
 * glide to their row (and back into the ring when you return). [states]: 0 faint, 1 hollow (running), 2 filled.
 * [morph] is a lambda so the animation is read in the draw phase only (no recomposition per frame).
 */
class RingDots(val states: List<Int>, val morph: () -> Float)

/**
 * Calm focus ring: one thin hairline track and a solid arc that fills as time passes. No gradient, no glow.
 * [fraction] is the elapsed share 0..1 (smoothed between the 250ms clock ticks); [color] changes smoothly,
 * e.g. when a Pomodoro switches from focus to break. Optional [dots] are the Pomodoro round dots.
 */
@Composable
fun FocusRing(
    fraction: Float,
    modifier: Modifier = Modifier,
    color: Color = FG.Primary,
    strokeWidth: Dp = 10.dp,
    dots: RingDots? = null,
    content: @Composable () -> Unit = {}
) {
    val progress by animateFloatAsState(
        fraction.coerceIn(0f, 1f), tween(260, easing = androidx.compose.animation.core.LinearEasing), label = "focusRing"
    )
    val tint by androidx.compose.animation.animateColorAsState(color, tween(500), label = "focusRingColor")
    val dotStates = dots?.states ?: emptyList()
    val dotFills = dotStates.map { st ->
        androidx.compose.animation.animateColorAsState(
            when (st) {
                2 -> FG.Primary
                1 -> FG.Primary.copy(alpha = 0f)
                else -> FG.Track
            },
            tween(450), label = "dotFill"
        ).value
    }
    val dotEdges = dotStates.map { st ->
        androidx.compose.animation.animateColorAsState(
            if (st == 0) FG.Track else FG.Primary, tween(450), label = "dotEdge"
        ).value
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = strokeWidth.toPx()
            val diameter = size.minDimension - stroke
            val arcSize = Size(diameter, diameter)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            drawArc(
                color = FG.Track,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            if (progress > 0.001f) {
                drawArc(
                    color = tint,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            // Round dots: pop out of the ring (staggered), glide to the row under the time.
            if (dots != null) {
                val m = dots.morph()
                val n = dotStates.size
                if (m > 0.001f && n > 0) {
                    val c = center
                    val ringR = diameter / 2f
                    val dotR = 4.5.dp.toPx()
                    val gap = 15.dp.toPx()
                    val rowY = c.y + 62.dp.toPx()
                    val step = 0.05f
                    val span = 1f - (n - 1) * step
                    for (i in 0 until n) {
                        val t = ((m - i * step) / span).coerceIn(0f, 1f)
                        if (t <= 0f) continue
                        val e = t * t * (3f - 2f * t)
                        val ang = Math.toRadians(-90.0 + 360.0 * i / n)
                        val fromX = c.x + ringR * kotlin.math.cos(ang).toFloat()
                        val fromY = c.y + ringR * kotlin.math.sin(ang).toFloat()
                        val toX = c.x + (i - (n - 1) / 2f) * gap
                        val pos = Offset(fromX + (toX - fromX) * e, fromY + (rowY - fromY) * e)
                        val r = dotR * minOf(1f, t * 5f)
                        drawCircle(color = dotFills[i], radius = r, center = pos)
                        drawCircle(color = dotEdges[i], radius = r - 1.dp.toPx(), center = pos, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
        }
        content()
    }
}

/**
 * Bar chart with growing bars, value labels, optional dashed limit line and day labels.
 * The last bar (today) uses the strong gradient, earlier days a soft tint.
 */
@Composable
fun BarChart(
    values: List<Long>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    limitMs: Long? = null,
    barAreaHeight: Dp = 130.dp
) {
    val maxValue = maxOf(values.maxOrNull() ?: 0L, limitMs ?: 0L, 60_000L).toFloat()
    val labelSpace = 18.dp
    val maxBar = barAreaHeight - labelSpace
    Column(modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(barAreaHeight)
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val maxBarPx = maxBar.toPx()
                val dash = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
                // faint baseline
                drawLine(
                    color = FG.Track,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2f
                )
                if (limitMs != null && limitMs > 0L) {
                    val y = size.height - maxBarPx * (limitMs / maxValue)
                    drawLine(
                        color = FG.Red,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 3f,
                        pathEffect = dash
                    )
                }
            }
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                values.forEachIndexed { i, v ->
                    // Entrance grow plays once; later changes of the value ease to the new height.
                    val animateIn = remember { !ChartIntro.played }
                    val grow = remember { Animatable(if (animateIn) 0f else 1f) }
                    LaunchedEffect(Unit) {
                        if (animateIn) {
                            delay(250 + i * 70L)
                            grow.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
                            if (i == values.lastIndex) ChartIntro.played = true
                        }
                    }
                    val fraction by animateFloatAsState(
                        targetValue = if (v <= 0L) 0f else (v / maxValue).coerceAtLeast(0.04f),
                        animationSpec = tween(500, easing = FastOutSlowInEasing),
                        label = "bar"
                    )
                    val today = i == values.lastIndex
                    val barHeight = maxOf(3.dp, maxBar * fraction)
                    Column(
                        Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            barLabel(v),
                            fontSize = 9.sp,
                            color = if (today) FG.Primary else FG.TextSecondary,
                            fontWeight = if (today) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = if (today) BoldFont else null,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .width(22.dp)
                                .height(barHeight)
                                // Grow in the draw phase (no re-layout / recomposition per frame).
                                .graphicsLayer {
                                    scaleY = grow.value
                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                }
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 3.dp, bottomEnd = 3.dp))
                                .background(
                                    when {
                                        v <= 0L -> FG.TrackBrush
                                        today -> FG.BarBrush
                                        else -> FG.SoftBarBrush
                                    }
                                )
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            labels.forEachIndexed { i, l ->
                Text(
                    l,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = if (i == labels.lastIndex) FontWeight.Bold else FontWeight.Normal,
                    fontFamily = if (i == labels.lastIndex) BoldFont else null,
                    color = if (i == labels.lastIndex) FG.Ink else FG.TextSecondary
                )
            }
        }
    }
}

/** 24 thin bars (one per hour of today). */
@Composable
fun HourlyChart(hours: LongArray, modifier: Modifier = Modifier, height: Dp = 72.dp) {
    val maxValue = maxOf(hours.maxOrNull() ?: 0L, 60_000L).toFloat()
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(height),
            verticalAlignment = Alignment.Bottom
        ) {
            for (h in 0 until 24) {
                val v = hours[h]
                val animateIn = remember { !ChartIntro.played }
                val grow = remember { Animatable(if (animateIn) 0f else 1f) }
                LaunchedEffect(Unit) {
                    if (animateIn) {
                        delay(250 + h * 25L)
                        grow.animateTo(1f, tween(500, easing = FastOutSlowInEasing))
                        if (h == 23) ChartIntro.played = true
                    }
                }
                val fraction by animateFloatAsState(
                    targetValue = if (v <= 0L) 0f else (v / maxValue).coerceAtLeast(0.06f),
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                    label = "hour"
                )
                val barHeight = maxOf(3.dp, height * fraction)
                Box(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 1.5.dp)
                        .height(barHeight)
                        .graphicsLayer {
                            scaleY = grow.value
                            transformOrigin = TransformOrigin(0.5f, 1f)
                        }
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                        .background(
                            if (v <= 0L) FG.TrackBrush else FG.BarBrush
                        )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("12am", "6am", "12pm", "6pm", "11pm").forEach {
                Text(it, fontSize = 10.sp, color = FG.TextSecondary)
            }
        }
    }
}
