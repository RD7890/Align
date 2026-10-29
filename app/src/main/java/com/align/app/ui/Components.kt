package com.align.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.animation.core.snap
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.align.app.data.AppCache
import com.align.app.data.Illustrations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Allowed button colours: white, black, #FF2541, green, red. */
enum class FgButtonStyle { Primary, Dark, Light, Outline, Success, Danger }

/**
 * Bouncy press animation + click, no ripple.
 * A plain composable modifier factory (not `composed`) so it stays cheap inside scrolling lists, and the
 * scale is only read while drawing, so pressing a row never recomposes it.
 */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow * 2.5f),
        label = "press"
    )
    return this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

@Composable
fun FgButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: FgButtonStyle = FgButtonStyle.Primary,
    enabled: Boolean = true,
    height: Dp = 54.dp
) {
    val (bg, fg, border) = when (style) {
        FgButtonStyle.Primary -> Triple<Color, Color, Color?>(FG.Primary, FG.White, null)
        FgButtonStyle.Dark -> Triple<Color, Color, Color?>(FG.Strong, FG.OnStrong, null)
        FgButtonStyle.Light -> Triple<Color, Color, Color?>(FG.Bg, FG.Ink, FG.Ink)
        FgButtonStyle.Outline -> Triple<Color, Color, Color?>(FG.Bg, FG.Red, FG.Red)
        FgButtonStyle.Success -> Triple<Color, Color, Color?>(FG.Green, FG.White, null)
        FgButtonStyle.Danger -> Triple<Color, Color, Color?>(FG.Red, FG.White, null)
    }
    val shape = RoundedCornerShape(18.dp)
    val animatedBg by animateColorAsState(bg, tween(200), label = "btnBg")
    Box(
        modifier = modifier
            .height(height)
            .pressable(enabled = enabled, onClick = onClick)
            .graphicsLayer { alpha = if (enabled) 1f else 0.4f }
            .clip(shape)
            .background(animatedBg)
            .then(if (border != null) Modifier.border(1.5.dp, border, shape) else Modifier)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
    }
}

/** Round black/white button that holds any content (icon or text). */
@Composable
fun FgRoundButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dark: Boolean = true,
    size: Dp = 44.dp,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(size)
            .pressable(onClick = onClick)
            .clip(CircleShape)
            .background(if (dark) FG.Strong else FG.Bg),
        contentAlignment = Alignment.Center
    ) { content() }
}

/**
 * Entrance animations are on during the first moments of a screen and off afterwards, so switching tabs
 * or scrolling lists never replays them (the tab switch has its own slide animation).
 */
val LocalEntranceAnimations = compositionLocalOf { true }

/** Fade + slide-up entrance, staggered by [index]. */
@Composable
fun Modifier.staggerIn(index: Int): Modifier {
    val enabled = LocalEntranceAnimations.current
    val progress = remember { Animatable(if (enabled) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (enabled) {
            delay((index * 70L).coerceAtMost(500L))
            progress.animateTo(1f, tween(480, easing = FastOutSlowInEasing))
        }
    }
    return this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 44.dp.toPx()
    }
}

/**
 * Downloaded unDraw illustration (assets/illustrations/<name>.svg) with a gentle float animation.
 *
 * The picture is inset by [inset] inside its box and the float is only a few dp, so it always stays
 * inside the (often clipped, rounded) container it lives in.
 */
@Composable
fun Illustration(
    name: String,
    modifier: Modifier = Modifier,
    floating: Boolean = true,
    inset: Dp = 10.dp
) {
    val ctx = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(
        initialValue = Illustrations.cached(name, ILLUSTRATION_WIDTH)?.asImageBitmap(),
        name
    ) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                Illustrations.render(ctx, name, ILLUSTRATION_WIDTH)?.asImageBitmap()
            }
        }
    }
    val transition = rememberInfiniteTransition(label = "float")
    val dy by transition.animateFloat(
        initialValue = -3.5f,
        targetValue = 3.5f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "dy"
    )
    val breathe by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathe"
    )
    val appear by animateFloatAsState(
        targetValue = if (bitmap != null) 1f else 0f,
        animationSpec = tween(450),
        label = "appear"
    )
    val panel = if (FG.isDark) Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White) else Modifier
    Box(modifier.then(panel), contentAlignment = Alignment.Center) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inset)
                    .graphicsLayer {
                        alpha = appear
                        if (floating) {
                            translationY = dy * density
                            scaleX = breathe
                            scaleY = breathe
                        }
                    }
            )
        }
    }
}

const val ILLUSTRATION_WIDTH = 720

@Composable
fun AppIconImage(pkg: String, size: Dp = 44.dp) {
    val ctx = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = AppCache.peekIcon(pkg), pkg) {
        if (value == null) {
            value = withContext(Dispatchers.Default) { AppCache.icon(ctx, pkg) }
        }
    }
    val bmp = icon
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(FG.Track))
    }
}

@Composable
fun Chip(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (bg == FG.Strong) FG.OnStrong else fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = BoldFont,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Sliding pill tab switcher. */
@Composable
fun FgTabs(titles: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(FG.Surface)
            .padding(4.dp)
    ) {
        val tabWidth = maxWidth / titles.size
        val offset by animateDpAsState(
            targetValue = tabWidth * selected,
            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
            label = "tabOffset"
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(FG.Primary)
        )
        Row(Modifier.fillMaxSize()) {
            titles.forEachIndexed { i, title ->
                val textColor by animateColorAsState(
                    if (i == selected) FG.White else FG.Ink, tween(200), label = "tabText"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(title, color = textColor, fontWeight = FontWeight.Bold, fontFamily = BoldFont, fontSize = 15.sp)
                }
            }
        }
    }
}

fun shortDuration(ms: Long): String {
    val min = ms / 60_000L
    return when {
        min >= 60 -> "${min / 60}h ${min % 60}m"
        min >= 1 -> "${min}m"
        ms >= 1000 -> "${ms / 1000}s"
        else -> "0m"
    }
}

/** Compact label for chart bars. */
fun barLabel(ms: Long): String {
    val min = ms / 60_000L
    return when {
        ms <= 0 -> ""
        min >= 60 -> String.format("%.1fh", ms / 3_600_000f)
        min >= 1 -> "${min}m"
        else -> "<1m"
    }
}

/** Short weekday names for the last 7 days, oldest first (today last). */
fun weekLabels(): List<String> {
    val fmt = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault())
    return (6 downTo 0).map {
        val c = java.util.Calendar.getInstance()
        c.add(java.util.Calendar.DAY_OF_YEAR, -it)
        fmt.format(c.time)
    }
}

fun hourLabel(h: Int): String {
    val h12 = if (h % 12 == 0) 12 else h % 12
    return "$h12 ${if (h < 12) "AM" else "PM"}"
}

/** Soft grey rounded container used for dashboard cards. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    androidx.compose.foundation.layout.Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(FG.Surface)
            .padding(18.dp),
        content = content
    )
}

// ---------------------------------------------------------------------------------------------
// Bottom navigation
// ---------------------------------------------------------------------------------------------

class NavItem(val label: String, @androidx.annotation.DrawableRes val icon: Int, @androidx.annotation.DrawableRes val iconFill: Int)

/**
 * Floating top bar, the twin of [FgBottomBar]: same width, height, shape, shadow, border and icon size.
 * Logo on the left (plus a FOCUS ON chip while a session runs), theme toggle on the right.
 */
@Composable
fun FgTopBar(
    running: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
    badgeColor: Color = FG.Primary
) {
    val shape = RoundedCornerShape(33.dp)
    val smooth = androidx.compose.animation.core.CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
    val (originTracker, markOrigin) = rememberThemeOrigin()
    // Keep the last badge while it animates out, so its text/colour don't jump.
    var lastBadge by remember { mutableStateOf(badge ?: "FOCUS ON") }
    var lastBadgeColor by remember { mutableStateOf(badgeColor) }
    val badgeText = badge ?: lastBadge
    val badgeTint = if (badge != null) badgeColor else lastBadgeColor
    androidx.compose.runtime.SideEffect {
        if (badge != null) {
            lastBadge = badge
            lastBadgeColor = badgeColor
        }
    }
    // Slow turn + cross-fade between the moon and the sun (in sync with the circular reveal).
    val spin by animateFloatAsState(if (FG.isDark) 180f else 0f, tween(800, easing = smooth), label = "themeSpin")
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .shadow(
                    16.dp, shape,
                    ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f)
                )
                .clip(shape)
                .background(FG.NavBg)
                .border(1.dp, if (FG.isDark) Color.White.copy(alpha = 0.06f) else FG.Track, shape)
                .padding(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(13.dp))
            Text("Al", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont, color = FG.Ink)
            Text("ign", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont, color = FG.Primary)
            // The badge grows out of the logo with a plain ease (no bounce) and the gap opens with it.
            androidx.compose.animation.AnimatedVisibility(
                visible = badge != null,
                enter = androidx.compose.animation.expandHorizontally(
                    tween(420, easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                    expandFrom = Alignment.Start
                ) + androidx.compose.animation.fadeIn(tween(360, delayMillis = 60)),
                exit = androidx.compose.animation.shrinkHorizontally(
                    tween(320, easing = androidx.compose.animation.core.CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
                    shrinkTowards = Alignment.Start
                ) + androidx.compose.animation.fadeOut(tween(200))
            ) {
                Row {
                    Spacer(Modifier.width(10.dp))
                    // Focus <-> Break swaps text and colour with a soft cross-fade.
                    androidx.compose.animation.Crossfade(
                        targetState = badgeText to badgeTint,
                        animationSpec = tween(320),
                        label = "badgeSwap"
                    ) { (t, c) -> Chip(t, c, Color.White) }
                }
            }
            Spacer(Modifier.weight(1f))
            // Theme toggle: same size as a bottom-nav item (52dp tall, 24dp icon).
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(52.dp)
                    .then(originTracker)
                    .graphicsLayer { alpha = if (running) 0.45f else 1f }
                    .pressable(onClick = {
                        markOrigin()
                        onToggleTheme()
                    })
                    .clip(RoundedCornerShape(26.dp))
                    .background(FG.Primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.graphicsLayer { rotationZ = spin }) {
                    androidx.compose.animation.Crossfade(
                        targetState = FG.isDark,
                        animationSpec = tween(650, easing = smooth),
                        label = "themeIcon"
                    ) { dark ->
                        androidx.compose.material3.Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                if (dark) com.align.app.R.drawable.ph_sun else com.align.app.R.drawable.ph_moon
                            ),
                            contentDescription = "Toggle theme",
                            tint = FG.Primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating pill navigation bar: the selected item grows into a red pill that shows its label, the icon
 * switches from the outline to the filled Phosphor icon with a little bounce.
 */
@Composable
fun FgBottomBar(items: List<NavItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .shadow(
                    16.dp, RoundedCornerShape(33.dp),
                    ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f)
                )
                .clip(RoundedCornerShape(33.dp))
                .background(FG.NavBg)
                .border(1.dp, if (FG.isDark) Color.White.copy(alpha = 0.06f) else FG.Track, RoundedCornerShape(33.dp))
                .padding(7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEachIndexed { i, item ->
                val sel = i == selected
                val weight by animateFloatAsState(
                    if (sel) 1.9f else 1f, spring(dampingRatio = 0.8f, stiffness = 420f), label = "navWeight"
                )
                val iconScale by animateFloatAsState(
                    if (sel) 1.1f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "navIcon"
                )
                val bg by animateColorAsState(if (sel) FG.Primary else Color.Transparent, tween(250), label = "navBg")
                val tint by animateColorAsState(
                    when {
                        sel -> Color.White
                        FG.isDark -> Color.White.copy(alpha = 0.6f)
                        else -> FG.TextSecondary
                    },
                    tween(250), label = "navTint"
                )
                Row(
                    Modifier
                        .weight(weight)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(26.dp))
                        .background(bg)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            onSelect(i)
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Icon(
                        painter = androidx.compose.ui.res.painterResource(if (sel) item.iconFill else item.icon),
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier
                            .size(24.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                    )
                    androidx.compose.animation.AnimatedVisibility(
                        visible = sel,
                        enter = androidx.compose.animation.fadeIn(tween(200, delayMillis = 80)) +
                            androidx.compose.animation.expandHorizontally(tween(260)),
                        exit = androidx.compose.animation.fadeOut(tween(120)) +
                            androidx.compose.animation.shrinkHorizontally(tween(200))
                    ) {
                        Text(
                            item.label,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = BoldFont,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Selectable pill (duration presets, category filters). */
@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val bg by animateColorAsState(if (selected) FG.Primary else FG.Surface, tween(200), label = "pillBg")
    val fg by animateColorAsState(if (selected) Color.White else FG.Ink, tween(200), label = "pillFg")
    Box(
        modifier
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .pressable(enabled = enabled, onClick = onClick)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont, maxLines = 1, softWrap = false)
    }
}

/** Press and hold for [seconds] to confirm (used to end a focus session early). */
@Composable
fun HoldButton(text: String, onComplete: () -> Unit, modifier: Modifier = Modifier, seconds: Float = 2.5f) {
    var progress by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier
            .height(54.dp)
            .clip(shape)
            .border(1.5.dp, FG.Red, shape)
            .background(FG.Bg)
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val job = scope.launch {
                        androidx.compose.animation.core.animate(
                            0f, 1f, animationSpec = tween((seconds * 1000).toInt(), easing = LinearEasing)
                        ) { v, _ -> progress = v }
                        onComplete()
                    }
                    tryAwaitRelease()
                    job.cancel()
                    progress = 0f
                })
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(FG.Red.copy(alpha = 0.22f))
        )
        Text(text, color = FG.Red, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
    }
}

@Composable
fun fgSwitchColors() = androidx.compose.material3.SwitchDefaults.colors(
    checkedThumbColor = Color.White,
    checkedTrackColor = FG.Primary,
    checkedBorderColor = FG.Primary,
    uncheckedThumbColor = Color.White,
    uncheckedTrackColor = FG.TextSecondary,
    uncheckedBorderColor = FG.TextSecondary,
    disabledCheckedThumbColor = Color.White,
    disabledCheckedTrackColor = FG.Primary.copy(alpha = 0.45f),
    disabledCheckedBorderColor = Color.Transparent,
    disabledUncheckedThumbColor = Color.White,
    disabledUncheckedTrackColor = FG.TextSecondary.copy(alpha = 0.45f),
    disabledUncheckedBorderColor = Color.Transparent
)

// ---------------------------------------------------------------------------------------------
// Slider
// ---------------------------------------------------------------------------------------------

/**
 * Custom slider: drag or tap anywhere on the track. Red gradient fill, a thumb that grows while it is
 * held, and a small haptic tick on every step.
 */
@Composable
fun FgSlider(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int,
    max: Int,
    step: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val inset = with(density) { 16.dp.toPx() }
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    val fraction by animateFloatAsState(
        targetValue = (value - min).toFloat() / (max - min),
        animationSpec = if (dragging) snap() else spring(dampingRatio = 0.8f, stiffness = 500f),
        label = "sliderFraction"
    )
    val thumbScale by animateFloatAsState(
        targetValue = if (dragging) 1.22f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumbScale"
    )

    fun update(x: Float) {
        val f = ((x - inset) / (widthPx - 2 * inset)).coerceIn(0f, 1f)
        val v = (Math.round((min + f * (max - min)) / step.toFloat()) * step).coerceIn(min, max)
        if (v != value) {
            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            onValueChange(v)
        }
    }

    Box(
        modifier
            .height(48.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.45f }
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled) {
                if (enabled) detectTapGestures { update(it.x) }
            }
            .pointerInput(enabled) {
                if (enabled) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragging = true; update(it.x) },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false }
                    ) { change, _ ->
                        change.consume()
                        update(change.position.x)
                    }
                }
            }
    ) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val trackH = 8.dp.toPx()
            val left = inset
            val right = size.width - inset
            val thumbX = left + (right - left) * fraction
            val corner = androidx.compose.ui.geometry.CornerRadius(trackH / 2f)
            drawRoundRect(
                color = FG.Track,
                topLeft = androidx.compose.ui.geometry.Offset(left, cy - trackH / 2f),
                size = androidx.compose.ui.geometry.Size(right - left, trackH),
                cornerRadius = corner
            )
            drawRoundRect(
                brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(FG.PrimaryLight, FG.Primary), startX = left, endX = right
                ),
                topLeft = androidx.compose.ui.geometry.Offset(left, cy - trackH / 2f),
                size = androidx.compose.ui.geometry.Size((thumbX - left).coerceAtLeast(0.1f), trackH),
                cornerRadius = corner
            )
            val r = 13.dp.toPx() * thumbScale
            drawCircle(
                color = Color.Black.copy(alpha = 0.18f), radius = r + 1.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(thumbX, cy + 2.dp.toPx())
            )
            drawCircle(color = Color.White, radius = r, center = androidx.compose.ui.geometry.Offset(thumbX, cy))
            drawCircle(
                color = FG.Primary, radius = r - 1.5.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(thumbX, cy),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
            )
        }
    }
}
