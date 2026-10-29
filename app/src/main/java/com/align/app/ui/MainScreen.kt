package com.align.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.widget.Toast
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.align.app.R
import com.align.app.data.AppCache
import com.align.app.data.AppEntry
import com.align.app.data.AppHelper
import com.align.app.data.LimitStore
import com.align.app.service.AutoStartHelper
import com.align.app.service.PomodoroEngine
import com.align.app.service.ServiceController
import com.align.app.widget.WidgetUpdater
import com.align.app.usage.UsageHelper
import com.align.app.usage.UsageHelper.UsageStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class Perms(val usage: Boolean, val overlay: Boolean, val notif: Boolean, val battery: Boolean) {
    val all: Boolean get() = usage && overlay && notif && battery
}

@SuppressLint("BatteryLife")
@Composable
fun MainScreen(
    tick: Int,
    visible: Boolean,
    themeMode: Int,
    onThemeChange: (Int) -> Unit,
    onRequestNotifications: () -> Unit,
    preloadedStats: UsageStats? = null
) {
    val ctx = LocalContext.current
    val store = remember { LimitStore(ctx) }

    var limits by remember { mutableStateOf(store.all()) }
    var monitoring by remember { mutableStateOf(store.monitoring) }
    var stats by remember { mutableStateOf<UsageStats?>(null) }
    // Numbers loaded behind the splash screen: the Stats tab opens with data already in place.
    LaunchedEffect(preloadedStats) {
        if (preloadedStats != null && stats == null) stats = preloadedStats
    }
    var tab by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    var editingPkg by remember { mutableStateOf<String?>(null) }
    var detailPkg by remember { mutableStateOf<String?>(null) }
    var detailOpen by remember { mutableStateOf(false) }
    var subPage by remember { mutableIntStateOf(0) } // 1 theme, 2 app limits, 3 blocked apps
    var subOpen by remember { mutableStateOf(false) }
    var autoStartDone by remember { mutableStateOf(store.autoStartConfirmed) }
    var showAutoStartPrompt by remember { mutableStateOf(false) }
    val homeListState = rememberLazyListState()
    val statsListState = rememberLazyListState()
    val settingsListState = rememberLazyListState()
    val limitsListState = rememberLazyListState()

    // ---- focus timer state ----
    var focusEnd by remember { mutableLongStateOf(store.focusEndAt) }
    var focusMin by remember { mutableIntStateOf(store.focusDurationMin) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var completed by remember { mutableStateOf(false) }
    var focusBlocked by remember { mutableStateOf(store.focusBlocked()) }
    var focusMode by remember { mutableIntStateOf(store.focusMode) } // 0 timer, 1 pomodoro
    var pomo by remember { mutableStateOf(store.pomo()) }
    var completedNote by remember { mutableStateOf<String?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    // derivedStateOf: only changes (and recomposes readers) when the session starts/ends, not on every clock tick.
    val running by remember { derivedStateOf { focusEnd > now } }

    // Entrance (stagger) animations only run during the first moments.
    var intro by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1800)
        intro = false
    }

    // Permission state. Re-read on resume and polled while something is missing.
    fun readPerms() = Perms(
        usage = UsageHelper.hasUsageAccess(ctx),
        overlay = Settings.canDrawOverlays(ctx),
        notif = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED,
        battery = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .isIgnoringBatteryOptimizations(ctx.packageName)
    )
    var perms by remember { mutableStateOf(readPerms()) }
    LaunchedEffect(tick, visible) {
        perms = readPerms()
        // The service may have ended the session while we were in the background.
        focusEnd = store.focusEndAt
        pomo = store.pomo()
        while (visible && !perms.all) {
            delay(350)
            val p = readPerms()
            if (p != perms) {
                perms = p
                ServiceController.sync(ctx)
            }
        }
    }
    val hasUsage = perms.usage
    val hasOverlay = perms.overlay
    val hasNotif = perms.notif
    val batteryOk = perms.battery

    // Timer clock: ticks while a session is running. Plain timer: finishes it when the time is up.
    // Pomodoro: the shared engine moves focus <-> break (it also runs in the service when the app is closed).
    LaunchedEffect(focusEnd, pomo.phase, pomo.end) {
        while (focusEnd > 0L || (pomo.phase != 0 && pomo.end != 0L)) {
            now = System.currentTimeMillis()
            if (pomo.phase != 0) {
                if (PomodoroEngine.advance(ctx, store, now, notify = !visible)) {
                    pomo = store.pomo()
                    focusEnd = store.focusEndAt
                    if (visible) haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    if (pomo.phase == 0) {
                        completed = true
                        completedNote = "All ${pomo.rounds} focus rounds done. Your apps are unlocked again."
                        ServiceController.sync(ctx)
                    }
                    break
                }
            } else if (now >= focusEnd) {
                store.focusEndAt = 0L
                focusEnd = 0L
                completed = true
                completedNote = null
                ServiceController.sync(ctx)
                break
            }
            delay(250)
        }
    }

    // First launch: pre-select likely distractions (social, video, games, news) - the user can change it.
    // Runs after the start-up animations so it never competes with them.
    LaunchedEffect(Unit) {
        if (!store.focusInitialized) {
            delay(2500)
            withContext(Dispatchers.IO) {
                store.setFocusBlocked(AppHelper.suggestedBlocked(ctx))
                store.focusInitialized = true
            }
            focusBlocked = store.focusBlocked()
        }
    }

    // Live usage numbers (today + last 7 days). Only loaded while something that shows them is open,
    // so the Home screen starts without any usage queries.
    val needStats = tab == 1 || (subOpen && subPage == 2) || detailOpen
    LaunchedEffect(visible, hasUsage, needStats) {
        if (visible && hasUsage && needStats) {
            if (stats != null) delay(2500) // already loaded (e.g. behind the splash): don't query again right away
            while (true) {
                val fresh = withContext(Dispatchers.IO) {
                    UsageHelper.loadStats(ctx, 7).also { AppCache.warm(ctx, it.todayByApp.keys) }
                }
                val current = stats
                if (current == null || !current.sameAs(fresh)) stats = fresh
                delay(8000)
            }
        }
    }

    fun refresh() {
        limits = store.all()
        ServiceController.sync(ctx)
        WidgetUpdater.updateAllAsync(ctx)
    }

    fun openSub(page: Int) {
        subPage = page
        subOpen = true
    }

    fun startFocus() {
        if (!perms.usage || !perms.overlay) {
            Toast.makeText(ctx, "Allow usage access and display-over-apps first.", Toast.LENGTH_LONG).show()
            openSub(2)
            return
        }
        store.focusDurationMin = focusMin
        val end = System.currentTimeMillis() + focusMin * 60_000L
        store.focusEndAt = end
        now = System.currentTimeMillis()
        focusEnd = end
        completed = false
        ServiceController.sync(ctx)
    }

    fun endFocus() {
        if (pomo.phase != 0 || store.pomoPhase != 0) PomodoroEngine.stop(store) else store.focusEndAt = 0L
        pomo = store.pomo()
        focusEnd = 0L
        ServiceController.sync(ctx)
    }

    fun startPomodoro() {
        if (!perms.usage || !perms.overlay) {
            Toast.makeText(ctx, "Allow usage access and display-over-apps first.", Toast.LENGTH_LONG).show()
            openSub(2)
            return
        }
        store.setPomoConfig(pomo)
        PomodoroEngine.start(store, System.currentTimeMillis())
        now = System.currentTimeMillis()
        pomo = store.pomo()
        focusEnd = store.focusEndAt
        completed = false
        completedNote = null
        ServiceController.sync(ctx)
    }

    /** Starts the waiting phase, or skips the running break. */
    fun nextPomodoro() {
        PomodoroEngine.startNext(store, System.currentTimeMillis())
        now = System.currentTimeMillis()
        pomo = store.pomo()
        focusEnd = store.focusEndAt
        if (pomo.phase == 0) {
            completed = true
            completedNote = "All ${pomo.rounds} focus rounds done. Your apps are unlocked again."
        }
        ServiceController.sync(ctx)
    }

    fun openAutoStart() {
        val message = when (AutoStartHelper.open(ctx)) {
            AutoStartHelper.Result.OEM_SCREEN -> null
            AutoStartHelper.Result.APP_SETTINGS ->
                "Open Battery / Background activity here and allow Align to run in the background."
            AutoStartHelper.Result.FAILED ->
                "Couldn't open settings. Go to Settings > Apps > Align and allow background activity."
        }
        if (message != null) Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
    }

    BackHandler(enabled = subOpen) { subOpen = false }
    BackHandler(enabled = picking && !detailOpen) { picking = false }
    BackHandler(enabled = detailOpen) { detailOpen = false }

    val navItems = remember {
        listOf(
            NavItem("Home", R.drawable.ph_timer, R.drawable.ph_timer_fill),
            NavItem("Stats", R.drawable.ph_chart_bar, R.drawable.ph_chart_bar_fill),
            NavItem("Settings", R.drawable.ph_gear_six, R.drawable.ph_gear_six_fill)
        )
    }

    CompositionLocalProvider(LocalEntranceAnimations provides intro) {
    Box(Modifier.fillMaxSize().background(FG.Bg)) {
        Scaffold(
            containerColor = FG.Bg
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = padding.calculateBottomPadding()),
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { if (forward) it / 3 else -it / 3 } +
                        fadeIn(tween(320, delayMillis = 60)))
                        .togetherWith(
                            slideOutHorizontally(tween(320, easing = FastOutSlowInEasing)) { if (forward) -it / 3 else it / 3 } +
                                fadeOut(tween(200))
                        )
                },
                label = "tabContent"
            ) { page ->
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = when (page) {
                        0 -> homeListState
                        1 -> statsListState
                        else -> settingsListState
                    },
                    // room for the floating bottom bar so the last items can scroll above it
                    contentPadding = PaddingValues(start = 20.dp, top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 100.dp, end = 20.dp, bottom = 128.dp)
                ) {
                    when (page) {
                        0 -> focusItems(
                            running = running,
                            remainingMs = { focusEnd - now },
                            durationMin = focusMin,
                            onDurationChange = {
                                focusMin = it
                                store.focusDurationMin = it
                            },
                            blocked = focusBlocked,
                            completed = completed,
                            permsOk = perms.usage && perms.overlay,
                            onStart = { startFocus() },
                            onEnd = { endFocus() },
                            onEditBlocked = { openSub(3) },
                            onSetup = { openSub(2) },
                            completedNote = completedNote,
                            mode = focusMode,
                            onModeChange = {
                                focusMode = it
                                store.focusMode = it
                                completed = false
                                completedNote = null
                            },
                            pomo = pomo,
                            nowMs = { now },
                            onPomoChange = {
                                pomo = it
                                store.setPomoConfig(it)
                            },
                            onPomoStart = { startPomodoro() },
                            onPomoNext = { nextPomodoro() }
                        )
                        1 -> dashboardItems(
                            stats = stats,
                            hasUsage = hasUsage,
                            limits = limits,
                            onOpenApp = { pkg ->
                                detailPkg = pkg
                                detailOpen = true
                            },
                            onGrantUsage = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        )
                        else -> settingsRootItems(
                            themeMode = themeMode,
                            limitCount = limits.size,
                            blockedCount = focusBlocked.size,
                            locked = running,
                            onTheme = { openSub(1) },
                            onLimits = { openSub(2) },
                            onBlocked = { openSub(3) }
                        )
                    }
                }
            }
        }

        // Solid strip behind the system status bar (clock, notifications, network, battery). It reaches 4dp below the
        // status bar and stops there: the header pill starts 12dp below the status bar, so the two never touch.
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 4.dp)
                .background(FG.Bg)
        )

        // Floating top bar (same style as the bottom bar) with the theme toggle at the top right.
        FgTopBar(
            running = running,
            // Timer: "FOCUS ON" while the session runs. Pomodoro: "FOCUS ON" in a focus round, "BREAK" in a break.
            badge = when {
                running -> "FOCUS ON"
                pomo.phase != 0 && pomo.end != 0L -> "BREAK"
                else -> null
            },
            badgeColor = if (running) FG.Primary else FG.Green,
            onToggleTheme = {
                if (running) {
                    Toast.makeText(ctx, "Theme is locked while a focus session is running.", Toast.LENGTH_SHORT).show()
                } else {
                    onThemeChange(if (FG.isDark) 1 else 2)
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // Floating bar: drawn over the content (no bar background / strip behind it).
        FgBottomBar(
            items = navItems,
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // Settings sub pages: Theme / App limits / Blocked apps
        AnimatedVisibility(
            visible = subOpen,
            enter = slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
        ) {
            when (subPage) {
                1 -> ThemeScreen(themeMode, running, onThemeChange) { subOpen = false }
                2 -> SettingsSubScreen("App limits", running, { subOpen = false }) {
                    Box(Modifier.weight(1f)) {
                        LazyColumn(
                            Modifier.fillMaxSize(),
                            state = limitsListState,
                            contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 100.dp)
                        ) {
                            limitsItems(
                                stats = stats,
                                limits = limits,
                                monitoring = monitoring,
                                hasUsage = hasUsage,
                                hasOverlay = hasOverlay,
                                hasNotif = hasNotif,
                                batteryOk = batteryOk,
                                onMonitoring = {
                                    monitoring = it
                                    store.monitoring = it
                                    refresh()
                                },
                                onRequestNotifications = onRequestNotifications,
                                onOpenApp = { pkg ->
                                    detailPkg = pkg
                                    detailOpen = true
                                },
                                onAdd = { picking = true },
                                autoStartDone = autoStartDone,
                                onAutoStartOpen = { openAutoStart() },
                                onAutoStartDone = {
                                    store.autoStartConfirmed = true
                                    autoStartDone = true
                                },
                                locked = running
                            )
                        }
                        FgButton(
                            text = "+  Add app limit",
                            onClick = { picking = true },
                            enabled = !running,
                            height = 56.dp,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 24.dp)
                        )
                    }
                }
                else -> BlockedAppsScreen(running, { focusBlocked = it }) { subOpen = false }
            }
        }

        AnimatedVisibility(
            visible = picking,
            enter = slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutVertically(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
        ) {
            AppPicker(
                limits = limits,
                onBack = { picking = false },
                onPick = { pkg ->
                    picking = false
                    editingPkg = pkg
                }
            )
        }

        AnimatedVisibility(
            visible = detailOpen,
            enter = slideInHorizontally(tween(380, easing = FastOutSlowInEasing)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(200))
        ) {
            detailPkg?.let { pkg ->
                // The detail screen always plays its own entrance.
                CompositionLocalProvider(LocalEntranceAnimations provides true) {
                    AppDetailScreen(
                        pkg = pkg,
                        limitMin = limits[pkg],
                        stats = stats,
                        locked = running,
                        onBack = { detailOpen = false },
                        onEdit = { editingPkg = pkg },
                        onRemove = {
                            store.remove(pkg)
                            refresh()
                            detailOpen = false
                        }
                    )
                }
            }
        }
    }
    }

    editingPkg?.let { pkg ->
        LimitDialog(
            label = remember(pkg) { AppCache.label(ctx, pkg) },
            initialMinutes = limits[pkg] ?: 30,
            onDismiss = { editingPkg = null },
            onSave = { minutes ->
                store.set(pkg, minutes)
                editingPkg = null
                refresh()
                // First limit ever saved: ask once to allow auto-start so the limit keeps working.
                if (!store.autoStartPrompted && !store.autoStartConfirmed) {
                    store.autoStartPrompted = true
                    showAutoStartPrompt = true
                }
            }
        )
    }

    if (showAutoStartPrompt) {
        AutoStartPromptDialog(
            family = remember { AutoStartHelper.family(ctx) },
            onOpen = {
                showAutoStartPrompt = false
                openAutoStart()
            },
            onLater = { showAutoStartPrompt = false }
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Dashboard tab: hero, donut (today), 7-day bars, all apps list
// ---------------------------------------------------------------------------------------------

private fun LazyListScope.dashboardItems(
    stats: UsageStats?,
    hasUsage: Boolean,
    limits: Map<String, Int>,
    onOpenApp: (String) -> Unit,
    onGrantUsage: () -> Unit
) {
    val loading = stats == null
    val today = stats?.todayByApp ?: emptyMap()
    // Tie-break by package so equal values never swap places between refreshes.
    val sorted = today.entries.sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
    val total = sorted.sumOf { it.value }
    val reached = limits.count { (pkg, min) -> (today[pkg] ?: 0L) >= min * 60_000L }

    item(key = "hero") {
        Spacer(Modifier.height(8.dp))
        HeroCard(
            totalText = if (loading) "–" else shortDuration(total),
            limited = limits.size,
            reached = reached,
            modifier = Modifier.staggerIn(0)
        )
    }

    if (!hasUsage) {
        item(key = "needs-usage") {
            Spacer(Modifier.height(16.dp))
            SectionCard(Modifier.staggerIn(1)) {
                Illustration("screen-time", Modifier.fillMaxWidth().height(170.dp))
                Spacer(Modifier.height(10.dp))
                Text("Allow usage access", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                Text(
                    "Align needs it to draw your usage charts and enforce limits.",
                    fontSize = 13.sp,
                    color = FG.TextSecondary
                )
                Spacer(Modifier.height(14.dp))
                FgButton("Grant usage access", onGrantUsage, Modifier.fillMaxWidth())
            }
        }
        return
    }

    item(key = "donut") {
        Spacer(Modifier.height(16.dp))
        SectionCard(Modifier.staggerIn(1)) {
            Text("Today by app", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
            Text("Where your time went", fontSize = 12.sp, color = FG.TextSecondary)
            Spacer(Modifier.height(14.dp))

            val top = sorted.take(5)
            val others = total - top.sumOf { it.value }
            val values = top.map { it.value.toFloat() } + (if (others > 0L) listOf(others.toFloat()) else emptyList())
            val colors = FG.SliceColors.take(top.size) + (if (others > 0L) listOf(FG.Neutral) else emptyList())

            // The chart has the same size in every state (loading / no usage / data), so nothing
            // jumps when the numbers arrive; only the ring fills in.
            DonutChart(
                values = values,
                colors = colors,
                centerTitle = when {
                    loading -> "–"
                    total <= 0L -> "0m"
                    else -> shortDuration(total)
                },
                centerSub = when {
                    loading -> "loading"
                    total <= 0L -> "no usage yet"
                    else -> "today"
                },
                modifier = Modifier
                    .size(210.dp)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(16.dp))

            // Loading skeleton and the empty hint reserve the same height as three legend rows;
            // when real rows arrive the block eases to its new height instead of snapping.
            Column(Modifier.fillMaxWidth().animateContentSize(tween(300, easing = FastOutSlowInEasing))) {
                when {
                    loading -> repeat(3) { LegendSkeletonRow() }
                    sorted.isEmpty() -> Box(
                        Modifier
                            .fillMaxWidth()
                            .height(LEGEND_PLACEHOLDER_HEIGHT),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Apps you use today will appear here.",
                            fontSize = 13.sp,
                            color = FG.TextSecondary
                        )
                    }
                    else -> {
                        top.forEachIndexed { i, e ->
                            LegendRow(
                                color = colors[i],
                                pkg = e.key,
                                ms = e.value,
                                percent = (e.value * 100f / total).toInt()
                            )
                        }
                        if (others > 0L) {
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(FG.Neutral))
                                Spacer(Modifier.width(10.dp))
                                Text("Other apps", Modifier.weight(1f), fontWeight = FontWeight.SemiBold, fontFamily = BoldFont, fontSize = 14.sp)
                                Text(shortDuration(others), fontSize = 13.sp, color = FG.TextSecondary)
                            }
                        }
                    }
                }
            }
        }
    }

    item(key = "week") {
        Spacer(Modifier.height(16.dp))
        SectionCard(Modifier.staggerIn(2)) {
            val days = stats?.dayTotals ?: List(7) { 0L }
            val avg = days.sum() / days.size
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Last 7 days", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                    Text("Total screen time per day", fontSize = 12.sp, color = FG.TextSecondary)
                }
                Chip("avg ${shortDuration(avg)}", FG.Primary, FG.White)
            }
            Spacer(Modifier.height(16.dp))
            BarChart(values = days, labels = weekLabels())
        }
    }

    item(key = "all-header") {
        Spacer(Modifier.height(22.dp))
        Text("All apps today", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
        Text("Tap an app for full details and limits", fontSize = 12.sp, color = FG.TextSecondary)
        Spacer(Modifier.height(8.dp))
    }

    when {
        loading -> items(3, key = { "usage-skeleton-$it" }) { AppRowSkeleton() }
        sorted.isEmpty() -> item(key = "all-empty") {
            Text(
                "Apps you use will show up here.",
                fontSize = 14.sp,
                color = FG.TextSecondary,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }
        else -> {
            val maxMs = sorted.first().value
            items(sorted.take(25), key = { "usage-" + it.key }, contentType = { "usage-row" }) { e ->
                AppUsageRow(
                    pkg = e.key,
                    usedMs = e.value,
                    maxMs = maxMs,
                    limitMin = limits[e.key],
                    onClick = { onOpenApp(e.key) }
                )
            }
        }
    }
}

/** Pulsing placeholder with the same height as a legend row (28dp icon + 2 x 6dp padding). */
@Composable
private fun LegendSkeletonRow() {
    val pulse = rememberSkeletonPulse()
    Row(
        Modifier
            .fillMaxWidth()
            .height(40.dp)
            .graphicsLayer { alpha = pulse.value },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(FG.Track))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(FG.Track))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(FG.Track))
        Spacer(Modifier.width(16.dp))
        Box(Modifier.width(44.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(FG.Track))
    }
}

/** Placeholder for an app usage row while the first stats load. */
@Composable
private fun AppRowSkeleton() {
    val pulse = rememberSkeletonPulse()
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .height(68.dp)
            .graphicsLayer { alpha = pulse.value }
            .clip(RoundedCornerShape(20.dp))
            .background(FG.Surface)
    )
}

@Composable
private fun rememberSkeletonPulse(): State<Float> {
    val transition = rememberInfiniteTransition(label = "skeleton")
    return transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
}

private val LEGEND_PLACEHOLDER_HEIGHT = 120.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(totalText: String, limited: Int, reached: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(30.dp))
            .background(FG.HeroBrush)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Screen time today", color = FG.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = BoldFont)
                Spacer(Modifier.height(4.dp))
                Text(
                    totalText,
                    color = FG.White,
                    fontSize = if (totalText.length > 6) 32.sp else 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = BoldFont,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(Modifier.height(12.dp))
                // Each pill stays on one line (any count); if both don't fit side by side the second
                // one moves below instead of squeezing the text onto two lines.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Chip("$limited limited", FG.White, FG.Primary)
                    Chip("$reached reached", FG.Strong, FG.OnStrong)
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .size(132.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(FG.White)
                    .padding(8.dp)
            ) {
                Illustration("time-management", Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun LegendRow(color: Color, pkg: String, ms: Long, percent: Int) {
    val ctx = LocalContext.current
    val label = remember(pkg) { AppCache.label(ctx, pkg) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        AppIconImage(pkg, 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            Modifier.weight(1f),
            fontWeight = FontWeight.SemiBold, fontFamily = BoldFont,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(shortDuration(ms), fontSize = 13.sp, color = FG.TextSecondary)
        Spacer(Modifier.width(8.dp))
        Text("$percent%", fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont, color = FG.Ink)
    }
}

@Composable
private fun AppUsageRow(pkg: String, usedMs: Long, maxMs: Long, limitMin: Int?, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val label = remember(pkg) { AppCache.label(ctx, pkg) }
    val fraction = animateFloatAsState(
        targetValue = if (maxMs > 0) usedMs.toFloat() / maxMs else 0f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "usageBar"
    )
    val over = limitMin != null && usedMs >= limitMin * 60_000L
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(20.dp))
            .background(FG.Surface)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIconImage(pkg, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    Modifier.weight(1f),
                    fontWeight = FontWeight.SemiBold, fontFamily = BoldFont,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(shortDuration(usedMs), fontWeight = FontWeight.Bold, fontFamily = BoldFont, fontSize = 14.sp)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .drawBehind {
                        val radius = CornerRadius(size.height / 2f)
                        drawRoundRect(color = FG.Track, cornerRadius = radius)
                        drawRoundRect(
                            brush = FG.HeroBrush,
                            size = Size(size.width * fraction.value.coerceIn(0.02f, 1f), size.height),
                            cornerRadius = radius
                        )
                    }
            )
            if (limitMin != null) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(
                        if (over) "Limit reached" else "Limit ${AppHelper.formatMinutes(limitMin)}",
                        if (over) FG.Red else FG.Strong,
                        FG.White
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Limits tab: toggle, setup, limited apps
// ---------------------------------------------------------------------------------------------

private fun LazyListScope.limitsItems(
    stats: UsageStats?,
    limits: Map<String, Int>,
    monitoring: Boolean,
    hasUsage: Boolean,
    hasOverlay: Boolean,
    hasNotif: Boolean,
    batteryOk: Boolean,
    onMonitoring: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenApp: (String) -> Unit,
    onAdd: () -> Unit,
    autoStartDone: Boolean,
    onAutoStartOpen: () -> Unit,
    onAutoStartDone: () -> Unit,
    locked: Boolean = false
) {
    val today = stats?.todayByApp ?: emptyMap()

    if (locked) {
        item(key = "locked") {
            Spacer(Modifier.height(8.dp))
            LockedBanner()
        }
    }

    item(key = "toggle") {
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .staggerIn(0)
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(FG.Surface)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("App blocking", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                Text(
                    if (monitoring) "On - limits are enforced" else "Off - limits are ignored",
                    fontSize = 13.sp,
                    color = FG.TextSecondary
                )
            }
            Switch(
                checked = monitoring,
                onCheckedChange = onMonitoring,
                enabled = !locked,
                colors = fgSwitchColors()
            )
        }
    }

    if (!hasUsage || !hasOverlay || !hasNotif || !batteryOk) {
        item(key = "setup") {
            Spacer(Modifier.height(16.dp))
            val ctx = LocalContext.current
            SectionCard(Modifier.staggerIn(1)) {
                Illustration("regain-focus", Modifier.fillMaxWidth().height(160.dp))
                Spacer(Modifier.height(8.dp))
                Text("Finish setup", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                Text(
                    "Blocking only works after the required permissions are granted.",
                    fontSize = 13.sp,
                    color = FG.TextSecondary
                )
                if (!hasUsage) {
                    PermissionRow(
                        "Usage access (required)",
                        "Lets the app see which app is open and for how long."
                    ) { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                }
                if (!hasOverlay) {
                    PermissionRow(
                        "Display over other apps (required)",
                        "Lets the block screen appear on top of the limited app."
                    ) {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${ctx.packageName}")
                            )
                        )
                    }
                }
                if (!hasNotif) {
                    PermissionRow(
                        "Notifications (recommended)",
                        "Shows the always-on status notification."
                    ) { onRequestNotifications() }
                }
                if (!batteryOk) {
                    PermissionRow(
                        "Unrestricted battery (recommended)",
                        "Stops the system from killing the monitor in the background."
                    ) {
                        ctx.startActivity(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${ctx.packageName}")
                            )
                        )
                    }
                }
            }
        }
    }

    item(key = "autostart") {
        Spacer(Modifier.height(16.dp))
        val ctx = LocalContext.current
        val family = remember { AutoStartHelper.family(ctx) }
        SectionCard(Modifier.staggerIn(2).animateContentSize(tween(300, easing = FastOutSlowInEasing))) {
            if (!autoStartDone) {
                Text("Keep Align running", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                Text(
                    "Allow auto-start so your limits keep working even after the app is closed or the phone restarts.",
                    fontSize = 13.sp,
                    color = FG.TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Chip(family.title, FG.Strong, FG.OnStrong)
                Spacer(Modifier.height(8.dp))
                Text(family.steps, fontSize = 12.sp, color = FG.TextSecondary)
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FgButton("Open settings", onAutoStartOpen, Modifier.weight(1f))
                    FgButton("Done", onAutoStartDone, Modifier.weight(1f), style = FgButtonStyle.Dark)
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-start allowed", fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                        Spacer(Modifier.height(4.dp))
                        Chip("CONFIRMED", FG.Green, FG.White)
                    }
                    FgButton("Review", onAutoStartOpen, style = FgButtonStyle.Light, height = 42.dp)
                }
            }
        }
    }

    item(key = "limits-header") {
        Spacer(Modifier.height(22.dp))
        Text("Limited apps", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
        Text(
            if (limits.isEmpty()) "Nothing limited yet" else "${limits.size} app(s) with a daily limit",
            fontSize = 12.sp,
            color = FG.TextSecondary
        )
        Spacer(Modifier.height(8.dp))
    }

    if (limits.isEmpty()) {
        item(key = "limits-empty") {
            SectionCard(Modifier.staggerIn(2)) {
                Illustration("meditating", Modifier.fillMaxWidth().height(200.dp))
                Spacer(Modifier.height(8.dp))
                Text(
                    "No limits yet",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold, fontFamily = BoldFont,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Text(
                    "Pick an app and set a daily time. Align steps in when it runs out.",
                    fontSize = 13.sp,
                    color = FG.TextSecondary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(14.dp))
                FgButton("Add app limit", onAdd, Modifier.fillMaxWidth(), enabled = !locked)
            }
        }
    } else {
        items(limits.entries.sortedBy { it.key }, key = { "limit-" + it.key }) { entry ->
            LimitRow(
                pkg = entry.key,
                limitMin = entry.value,
                usedMs = today[entry.key] ?: 0L,
                onClick = { onOpenApp(entry.key) }
            )
        }
    }
}

@Composable
private fun PermissionRow(title: String, description: String, onGrant: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = BoldFont)
            Text(description, fontSize = 12.sp, color = FG.TextSecondary)
        }
        Spacer(Modifier.width(10.dp))
        FgButton("Grant", onGrant, height = 42.dp)
    }
}

@Composable
private fun LimitRow(pkg: String, limitMin: Int, usedMs: Long, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val label = remember(pkg) { AppCache.label(ctx, pkg) }
    val limitMs = limitMin * 60_000L
    val over = usedMs >= limitMs
    val fraction = (usedMs.toFloat() / limitMs.toFloat()).coerceIn(0f, 1f)
    val left = (limitMs - usedMs).coerceAtLeast(0L)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(24.dp))
            .background(FG.Surface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RingProgress(
            fraction = fraction,
            modifier = Modifier.size(66.dp),
            ringBrush = if (over) Brush.linearGradient(listOf(FG.Red, FG.Red))
            else Brush.linearGradient(listOf(FG.PrimaryLight, FG.Primary)),
            strokeWidth = 6.dp
        ) {
            AppIconImage(pkg, 34.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.Bold, fontFamily = BoldFont, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${shortDuration(usedMs)} of ${AppHelper.formatMinutes(limitMin)} today",
                fontSize = 12.sp,
                color = FG.TextSecondary
            )
            Spacer(Modifier.height(6.dp))
            Chip(
                if (over) "LIMIT REACHED" else "${shortDuration(left)} left",
                if (over) FG.Red else FG.Green,
                FG.White
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// App picker + limit dialog
// ---------------------------------------------------------------------------------------------

@Composable
private fun AppPicker(limits: Map<String, Int>, onBack: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { AppHelper.launchableApps(ctx) }
    }

    Surface(Modifier.fillMaxSize(), color = FG.Bg) {
        Column(Modifier.statusBarsPadding()) {
            Row(
                Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FgRoundButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FG.OnStrong)
                }
                Spacer(Modifier.width(14.dp))
                Text("Select an app", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search apps") },
                shape = RoundedCornerShape(18.dp),
                colors = fieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
            val list = apps
            if (list == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = FG.Primary)
                }
            } else {
                val filtered = list.filter { it.label.contains(query.trim(), ignoreCase = true) }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp)
                ) {
                    items(filtered, key = { it.pkg }) { app ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .pressable { onPick(app.pkg) }
                                .clip(RoundedCornerShape(18.dp))
                                .background(FG.Surface)
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconImage(app.pkg, 44.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                app.label,
                                Modifier.weight(1f),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold, fontFamily = BoldFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            limits[app.pkg]?.let {
                                Chip(AppHelper.formatMinutes(it), FG.Strong, FG.OnStrong)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = FG.Primary,
    unfocusedBorderColor = FG.Track,
    cursorColor = FG.Primary,
    focusedLabelColor = FG.Primary,
    unfocusedLabelColor = FG.TextSecondary,
    focusedTextColor = FG.Ink,
    unfocusedTextColor = FG.Ink
)

@Composable
private fun LimitDialog(
    label: String,
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
) {
    var text by remember { mutableStateOf(initialMinutes.toString()) }
    val minutes = text.toIntOrNull()
    val valid = minutes != null && minutes in 1..1440

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FG.Bg,
        shape = RoundedCornerShape(28.dp),
        title = { Text(label, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont) },
        text = {
            Column {
                Text("Daily time limit", fontSize = 14.sp, color = FG.TextSecondary)
                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 5, 15, 30, 60, 120).forEach { m ->
                        FilterChip(
                            selected = minutes == m,
                            onClick = { text = m.toString() },
                            label = { Text(AppHelper.formatMinutes(m), fontWeight = FontWeight.SemiBold, fontFamily = BoldFont) },
                            shape = RoundedCornerShape(14.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = FG.Bg,
                                labelColor = FG.Ink,
                                selectedContainerColor = FG.Primary,
                                selectedLabelColor = FG.White
                            )
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { v -> text = v.filter { it.isDigit() }.take(4) },
                    label = { Text("Minutes per day") },
                    singleLine = true,
                    isError = !valid,
                    shape = RoundedCornerShape(16.dp),
                    colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            FgButton(
                text = "Save",
                onClick = { if (minutes != null) onSave(minutes) },
                enabled = valid,
                height = 46.dp
            )
        },
        dismissButton = {
            FgButton(text = "Cancel", onClick = onDismiss, style = FgButtonStyle.Light, height = 46.dp)
        }
    )
}

@Composable
private fun AutoStartPromptDialog(
    family: AutoStartHelper.Family,
    onOpen: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        containerColor = FG.Bg,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Allow auto-start?", fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont) },
        text = {
            Column {
                Text(
                    "To keep your limits working after you close Align or restart your phone, allow it to start automatically.",
                    fontSize = 14.sp,
                    color = FG.TextSecondary
                )
                Spacer(Modifier.height(10.dp))
                Chip(family.title, FG.Strong, FG.OnStrong)
                Spacer(Modifier.height(8.dp))
                Text(family.steps, fontSize = 13.sp)
            }
        },
        confirmButton = { FgButton("Open settings", onOpen, height = 46.dp) },
        dismissButton = { FgButton("Later", onLater, style = FgButtonStyle.Light, height = 46.dp) }
    )
}
