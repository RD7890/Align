package com.align.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.align.app.data.Illustrations
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.hypot

private const val LOGO_WIDTH = 640

/**
 * Animated launch screen (plays once per cold start, on top of the already-composing app):
 * red gradient -> shield pops in with a spring -> hourglass appears and flips -> two ripples ->
 * wordmark and tagline slide up -> exit: text slides away, the logo shrinks into the shield and a circular
 * reveal opens from the logo, uncovering the app, which scales up slightly into place ([reveal] 0..1).
 *
 * Logo = Phosphor "shield" + "hourglass-simple" (MIT) from assets/brand.
 */
@Composable
fun SplashScreen(
    reveal: Animatable<Float, AnimationVector1D>,
    dataReady: () -> Boolean = { true },
    onFinished: () -> Unit
) {
    val ctx = LocalContext.current

    val shield by produceState<ImageBitmap?>(
        initialValue = Illustrations.cached("brand/shield", LOGO_WIDTH)?.asImageBitmap()
    ) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                Illustrations.render(ctx, "brand/shield", LOGO_WIDTH)?.asImageBitmap()
            }
        }
    }
    val hourglass by produceState<ImageBitmap?>(
        initialValue = Illustrations.cached("brand/hourglass", LOGO_WIDTH)?.asImageBitmap()
    ) {
        if (value == null) {
            value = withContext(Dispatchers.Default) {
                Illustrations.render(ctx, "brand/hourglass", LOGO_WIDTH)?.asImageBitmap()
            }
        }
    }
    val ready = shield != null && hourglass != null

    val shieldScale = remember { Animatable(0.5f) }
    val shieldAlpha = remember { Animatable(0f) }
    val hourAlpha = remember { Animatable(0f) }
    val hourScale = remember { Animatable(0.4f) }
    val hourRotation = remember { Animatable(0f) }
    val ripple1 = remember { Animatable(0f) }
    val ripple2 = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    val tagline = remember { Animatable(0f) }
    val logoExit = remember { Animatable(0f) }
    val hole = remember { Animatable(0f) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var logoCenter by remember { mutableStateOf(Offset.Unspecified) }

    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        coroutineScope {
            launch { shieldAlpha.animateTo(1f, tween(260)) }
            launch {
                shieldScale.animateTo(
                    1f,
                    spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)
                )
            }
            launch { delay(260); hourAlpha.animateTo(1f, tween(240)) }
            launch {
                delay(260)
                hourScale.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
            }
            launch { delay(800); hourRotation.animateTo(180f, tween(750, easing = FastOutSlowInEasing)) }
            launch { delay(250); ripple1.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing)) }
            launch { delay(600); ripple2.animateTo(1f, tween(1200, easing = LinearOutSlowInEasing)) }
            launch { delay(700); title.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
            launch { delay(950); tagline.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
        }
        delay(350)
        // The splash is there to load things: hold the finished logo until the data behind it is ready
        // (never longer than 5 s, so a slow device can't get stuck here).
        withTimeoutOrNull(5000) { snapshotFlow { dataReady() }.first { it } }

        // ---- exit ----
        val smooth = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)
        coroutineScope {
            launch { title.animateTo(0f, tween(260, easing = FastOutSlowInEasing)) }
            launch { tagline.animateTo(0f, tween(200, easing = FastOutSlowInEasing)) }
            launch { logoExit.animateTo(1f, tween(620, easing = smooth)) }
            launch { delay(140); hole.animateTo(1f, tween(720, easing = smooth)) }
            launch { delay(140); reveal.animateTo(1f, tween(720, easing = smooth)) }
        }
        onFinished()
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
            // Offscreen layer so BlendMode.Clear can punch a transparent circle through the splash.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val p = hole.value
                if (p > 0f && size.width > 0f && size.height > 0f) {
                    val c = if (logoCenter == Offset.Unspecified) center else logoCenter - rootOrigin
                    val radius = p * hypot(size.width, size.height)
                    drawCircle(color = Color.Black, radius = radius, center = c, blendMode = BlendMode.Clear)
                    // thin light rim on the edge of the opening
                    drawCircle(
                        color = Color.White.copy(alpha = 0.35f * (1f - p)),
                        radius = radius,
                        center = c,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }
            .background(Brush.linearGradient(listOf(FG.PrimaryLight, FG.Primary, FG.PrimaryDark)))
            .pointerInput(Unit) { }, // swallow touches while the splash is on screen
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(132.dp)
                    .onGloballyPositioned {
                        val p = it.positionInRoot()
                        logoCenter = Offset(p.x + it.size.width / 2f, p.y + it.size.height / 2f)
                    }
                    .drawBehind {
                        // soft glow + two expanding ripples around the logo (not clipped to the box)
                        val glow = 150.dp.toPx() * (1f - logoExit.value)
                        if (glow > 0f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(Color.White.copy(alpha = 0.22f), Color.Transparent),
                                center = center,
                                radius = glow
                            ),
                            radius = glow,
                            center = center
                        )
                        }
                        for (p in listOf(ripple1.value, ripple2.value)) {
                            if (p > 0f && p < 1f) {
                                drawCircle(
                                    color = Color.White.copy(alpha = 0.32f * (1f - p)),
                                    radius = 70.dp.toPx() + p * 190.dp.toPx(),
                                    center = center,
                                    style = Stroke(width = 2.dp.toPx())
                                )
                            }
                        }
                    }
            ) {
                shield?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val exit = 1f - 0.35f * logoExit.value
                                scaleX = shieldScale.value * exit
                                scaleY = shieldScale.value * exit
                                alpha = shieldAlpha.value * (1f - logoExit.value)
                            }
                    )
                }
                hourglass?.let { bmp ->
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val exit = 1f - 0.35f * logoExit.value
                                scaleX = hourScale.value * exit
                                scaleY = hourScale.value * exit
                                alpha = hourAlpha.value * (1f - logoExit.value)
                                rotationZ = hourRotation.value
                            }
                    )
                }
            }
            Spacer(Modifier.height(30.dp))
            Row(
                Modifier.graphicsLayer {
                    alpha = title.value
                    translationY = (1f - title.value) * 24.dp.toPx()
                }
            ) {
                Text("Al", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont)
                Text("ign", color = FG.PrimarySoft, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Own your time.",
                color = Color.White.copy(alpha = 0.88f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer {
                    alpha = tagline.value
                    translationY = (1f - tagline.value) * 16.dp.toPx()
                }
            )
        }
    }
}
