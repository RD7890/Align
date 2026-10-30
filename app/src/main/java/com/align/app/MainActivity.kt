package com.align.app

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.align.app.ui.ThemeReveal
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import com.align.app.data.AppCache
import com.align.app.data.StorageFolders
import com.align.app.usage.UsageHelper
import com.align.app.usage.UsageHelper.UsageStats
import kotlinx.coroutines.coroutineScope
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.mutableLongStateOf
import com.align.app.ui.OnboardingScreen
import com.align.app.data.Illustrations
import com.align.app.data.LimitStore
import com.align.app.service.ServiceController
import com.align.app.widget.WidgetUpdater
import com.align.app.ui.AlignTheme
import com.align.app.ui.ILLUSTRATION_WIDTH
import com.align.app.ui.MainScreen
import com.align.app.ui.SplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val tick = mutableIntStateOf(0)
    private val visible = mutableStateOf(false)

    private val notificationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            tick.intValue = tick.intValue + 1
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val coldStart = savedInstanceState == null
        // Create Android/data|obb|media/<package> for later use (off the main thread, no permission needed).
        Thread { StorageFolders.ensure(applicationContext) }.start()
        val themeStore = LimitStore(this)
        if (!themeStore.onboardingChecked) {
            themeStore.onboardingChecked = true
            if (themeStore.focusInitialized) themeStore.onboardingDone = true // already an existing user
        }
        val themeMode = mutableIntStateOf(themeStore.themeMode)
        setContent {
            // System bar icons follow the theme (dark icons on light UI, light icons on dark UI).
            val systemDark = isSystemInDarkTheme()
            val dark = themeMode.intValue == 2 || (themeMode.intValue == 0 && systemDark)
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                    else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                )
                onDispose { }
            }
            AlignTheme(themeMode.intValue) {
                // Animated splash only on a fresh launch; it plays on top of the app, which is already
                // composing and loading underneath, then slides away.
                var showSplash by rememberSaveable { mutableStateOf(coldStart) }
                val ctx = LocalContext.current
                // Everything the tabs need is loaded while the splash plays (and the splash waits for it, with a
                // time limit): illustrations, the 7-day usage stats behind the charts and numbers, and the app
                // icons / labels shown in the lists. The Stats tab then opens with data already in place.
                val preloadedStats = remember { mutableStateOf<UsageStats?>(null) }
                val dataReady = remember { mutableStateOf(!coldStart) }
                LaunchedEffect(Unit) {
                    withContext(Dispatchers.Default) {
                        coroutineScope {
                            launch {
                                Illustrations.preload(
                                    ctx,
                                    listOf("time-management", "meditating", "regain-focus"),
                                    ILLUSTRATION_WIDTH
                                )
                            }
                            if (coldStart) {
                                launch {
                                    try {
                                        val store = LimitStore(ctx)
                                        if (UsageHelper.hasUsageAccess(ctx)) {
                                            val fresh = UsageHelper.loadStats(ctx, 7)
                                            AppCache.warm(ctx, fresh.todayByApp.keys, iconLimit = 14)
                                            preloadedStats.value = fresh
                                        }
                                        AppCache.warm(ctx, store.all().keys + store.focusBlocked(), iconLimit = 14)
                                    } catch (_: Throwable) {
                                        // preloading is best effort: the tabs load their own data if this fails
                                    }
                                }
                            }
                        }
                    }
                    dataReady.value = true
                }
                // 0 = splash fully covering the app, 1 = app revealed. The app eases from 94% to 100% size
                // while the splash opens a circular reveal (see SplashScreen).
                val reveal = remember { Animatable(if (coldStart) 0f else 1f) }

                // Theme change with a circular reveal: screenshot the old theme, apply the new one underneath
                // and open a growing hole in the screenshot, starting at the control that was tapped.
                val themeSnapshot = remember { mutableStateOf<ImageBitmap?>(null) }
                val themeReveal = remember { Animatable(0f) }
                val revealScope = rememberCoroutineScope()
                val applyTheme: (Int) -> Unit = {
                    themeStore.themeMode = it
                    themeMode.intValue = it
                }
                val changeTheme: (Int) -> Unit = { mode ->
                    if (mode != themeMode.intValue) {
                        if (themeSnapshot.value != null) {
                            applyTheme(mode) // a reveal is already running: just switch
                        } else {
                            ThemeReveal.capture(this@MainActivity) { bmp ->
                                if (bmp == null) {
                                    applyTheme(mode)
                                } else {
                                    themeSnapshot.value = bmp.asImageBitmap()
                                    applyTheme(mode)
                                    revealScope.launch {
                                        themeReveal.snapTo(0f)
                                        themeReveal.animateTo(
                                            1f, tween(800, easing = CubicBezierEasing(0.65f, 0f, 0.35f, 1f))
                                        )
                                        themeSnapshot.value = null
                                    }
                                }
                            }
                        }
                    }
                }
                // First-run welcome / permissions / final note. Stays until the user finishes it.
                var showOnboarding by rememberSaveable { mutableStateOf(!themeStore.onboardingDone) }

                // Double back to exit. This registers first, so MainScreen's own back handlers (closing a sheet, a
                // detail page, the onboarding steps) win while they are active. finish() only closes the screen:
                // the monitor service and its foreground notification keep running.
                var lastBackAt by remember { mutableLongStateOf(0L) }
                val exitToast = remember { arrayOfNulls<Toast>(1) }
                BackHandler(enabled = true) {
                    val t = SystemClock.elapsedRealtime()
                    if (t - lastBackAt < 2000L) {
                        exitToast[0]?.cancel()
                        finish()
                    } else {
                        lastBackAt = t
                        exitToast[0]?.cancel()
                        exitToast[0] = Toast.makeText(ctx, "Press back again to exit", Toast.LENGTH_SHORT).also { it.show() }
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val s = 0.94f + 0.06f * reveal.value
                                scaleX = s
                                scaleY = s
                            }
                    ) {
                        MainScreen(
                            tick = tick.intValue,
                            visible = visible.value,
                            themeMode = themeMode.intValue,
                            onThemeChange = changeTheme,
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            preloadedStats = preloadedStats.value
                        )
                    }
                    AnimatedVisibility(
                        visible = showOnboarding,
                        enter = EnterTransition.None,
                        exit = fadeOut(tween(350)) + scaleOut(tween(350), targetScale = 1.04f)
                    ) {
                        OnboardingScreen(
                            onRequestNotifications = {
                                if (Build.VERSION.SDK_INT >= 33) {
                                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onFinish = {
                                themeStore.onboardingDone = true
                                showOnboarding = false
                                Toast.makeText(ctx, "Thank you for using Align!", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                    // Old-theme screenshot with a circular hole growing from the tapped control.
                    val snap = themeSnapshot.value
                    if (snap != null) {
                        Spacer(
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawBehind {
                                    drawImage(snap, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                                    val c = if (ThemeReveal.origin == Offset.Unspecified) center else ThemeReveal.origin
                                    val maxR = hypot(max(c.x, size.width - c.x), max(c.y, size.height - c.y))
                                    drawCircle(
                                        color = androidx.compose.ui.graphics.Color.Black,
                                        radius = themeReveal.value * maxR,
                                        center = c,
                                        blendMode = BlendMode.Clear
                                    )
                                }
                                .pointerInput(Unit) { } // swallow touches while the reveal plays
                        )
                    }
                    // The splash animates its own exit, so it's removed instantly when done.
                    AnimatedVisibility(visible = showSplash, exit = ExitTransition.None) {
                        SplashScreen(reveal = reveal, dataReady = { dataReady.value }, onFinished = { showSplash = false })
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        visible.value = true
        tick.intValue = tick.intValue + 1
        // Start/stop the background monitor depending on permissions + saved limits.
        ServiceController.sync(this)
        WidgetUpdater.updateAllAsync(this, 3000L) // not while the app is still starting up
    }

    override fun onPause() {
        super.onPause()
        visible.value = false
    }
}
