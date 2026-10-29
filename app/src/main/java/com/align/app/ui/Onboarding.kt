package com.align.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.align.app.R
import com.align.app.data.LimitStore
import com.align.app.service.AutoStartHelper
import com.align.app.usage.UsageHelper
import kotlinx.coroutines.delay

private val Ease = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

private data class Granted(val usage: Boolean, val battery: Boolean, val notif: Boolean, val overlay: Boolean)

private fun readGranted(ctx: Context) = Granted(
    usage = UsageHelper.hasUsageAccess(ctx),
    battery = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(ctx.packageName),
    notif = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
    overlay = Settings.canDrawOverlays(ctx)
)

/**
 * First-run flow, three pages: 1) what Align is and why it exists, 2) the permissions it needs, asked one by
 * one in a fixed order (usage access, battery, notifications, display over other apps, auto-start),
 * 3) a closing note. Shown once; [onFinish] stores that and shows the thank-you toast.
 */
@Composable
fun OnboardingScreen(onRequestNotifications: () -> Unit, onFinish: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = page > 0) { page -= 1 }

    Box(
        Modifier
            .fillMaxSize()
            .background(FG.Bg)
            .pointerInput(Unit) { } // swallow touches: the app underneath must not react
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(18.dp))
            PageDots(page = page, count = 3)
            Spacer(Modifier.height(8.dp))

            Box(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = {
                        val forward = targetState > initialState
                        (slideInHorizontally(tween(420, easing = Ease)) { if (forward) it / 4 else -it / 4 } +
                            fadeIn(tween(360, easing = Ease)))
                            .togetherWith(
                                slideOutHorizontally(tween(320, easing = Ease)) { if (forward) -it / 4 else it / 4 } +
                                    fadeOut(tween(200, easing = Ease))
                            )
                    },
                    label = "onboardingPage"
                ) { p ->
                    when (p) {
                        0 -> AboutPage()
                        1 -> PermissionsPage(onRequestNotifications)
                        else -> FinalPage()
                    }
                }
            }

            Column(Modifier.padding(vertical = 16.dp)) {
                FgButton(
                    text = when (page) {
                        0 -> "Next"
                        1 -> "Continue"
                        else -> "Start using Align"
                    },
                    onClick = { if (page < 2) page += 1 else onFinish() },
                    modifier = Modifier.fillMaxWidth(),
                    height = 58.dp
                )
                if (page > 0) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .pressable(onClick = { page -= 1 }),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Back", fontSize = 14.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont, color = FG.TextSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun PageDots(page: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 0 until count) {
            val width by animateDpAsState(if (i == page) 28.dp else 8.dp, tween(350, easing = Ease), label = "dotWidth")
            val color by animateColorAsState(if (i <= page) FG.Primary else FG.Track, tween(350), label = "dotColor")
            Box(
                Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Page 1: about
// ---------------------------------------------------------------------------------------------

@Composable
private fun AboutPage() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Illustration("regain-focus", Modifier.fillMaxWidth().height(200.dp))
        Spacer(Modifier.height(10.dp))
        Chip("ABOUT ALIGN", FG.Strong, FG.OnStrong)
        Spacer(Modifier.height(12.dp))
        Text(
            "Take your attention\nback.",
            fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont, color = FG.Ink
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Your phone is built by some of the richest companies on earth, like Meta and Google, and their business " +
                "is your attention. Every endless feed, red badge and notification is designed to give you a tiny " +
                "dopamine hit, so you come back for one more, again and again.",
            fontSize = 15.sp, lineHeight = 22.sp, color = FG.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Align is my answer to that. It helps you set honest limits, focus deeply and escape the scroll trap, " +
                "so you decide where your attention goes, not an algorithm.",
            fontSize = 15.sp, lineHeight = 22.sp, color = FG.TextSecondary
        )
        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(FG.Primary),
                contentAlignment = Alignment.Center
            ) {
                Text("R", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont, color = FG.White)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Rohan Dora", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont, color = FG.Ink)
                Text("Built Align", fontSize = 12.sp, color = FG.TextSecondary)
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Page 2: permissions, one at a time, in a fixed order
// ---------------------------------------------------------------------------------------------

private class PermStep(
    val icon: Int,
    val title: String,
    val why: String,
    val granted: Boolean,
    val primaryLabel: String,
    val onPrimary: () -> Unit,
    val secondaryLabel: String,
    val onSecondary: () -> Unit,
    val extraText: String? = null,
    val extraChip: String? = null
)

@Composable
private fun PermissionsPage(onRequestNotifications: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { LimitStore(ctx) }
    var granted by remember { mutableStateOf(readGranted(ctx)) }
    var autoDone by remember { mutableStateOf(store.autoStartConfirmed) }
    var skipped by rememberSaveable { mutableIntStateOf(0) } // bit i = step i skipped

    // The user leaves for system settings and comes back: poll so the next step opens by itself.
    LaunchedEffect(Unit) {
        while (true) {
            val g = readGranted(ctx)
            if (g != granted) granted = g
            delay(350)
        }
    }

    val family = remember { AutoStartHelper.family(ctx) }
    fun skip(i: Int) {
        skipped = skipped or (1 shl i)
    }

    val steps = listOf(
        PermStep(
            icon = R.drawable.ph_chart_bar,
            title = "Usage access",
            why = "Lets Align see which app is open and for how long, so your limits can work.",
            granted = granted.usage,
            primaryLabel = "Allow",
            onPrimary = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
            secondaryLabel = "Not now",
            onSecondary = { skip(0) }
        ),
        PermStep(
            icon = R.drawable.ph_battery_charging,
            title = "Unrestricted battery",
            why = "Stops the system from putting Align to sleep, so blocking keeps working in the background.",
            granted = granted.battery,
            primaryLabel = "Allow",
            onPrimary = {
                ctx.startActivity(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                )
            },
            secondaryLabel = "Not now",
            onSecondary = { skip(1) }
        ),
        PermStep(
            icon = R.drawable.ph_bell,
            title = "Notifications",
            why = "Shows the small always-on status notification and your Pomodoro break alerts.",
            granted = granted.notif,
            primaryLabel = "Allow",
            onPrimary = { onRequestNotifications() },
            secondaryLabel = "Not now",
            onSecondary = { skip(2) }
        ),
        PermStep(
            icon = R.drawable.ph_device_mobile,
            title = "Display over other apps",
            why = "Lets the Time's up screen appear on top of an app that reached its limit. Without it nothing can be blocked.",
            granted = granted.overlay,
            primaryLabel = "Allow",
            onPrimary = {
                ctx.startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                )
            },
            secondaryLabel = "Not now",
            onSecondary = { skip(3) }
        ),
        PermStep(
            icon = R.drawable.ph_arrows_clockwise,
            title = "Auto-start",
            why = "Lets Align start again after a restart or after being swiped away, so your limits never stop.",
            granted = autoDone,
            primaryLabel = "Open settings",
            onPrimary = {
                val message = when (AutoStartHelper.open(ctx)) {
                    AutoStartHelper.Result.OEM_SCREEN -> null
                    AutoStartHelper.Result.APP_SETTINGS ->
                        "Open Battery / Background activity here and allow Align to run in the background."
                    AutoStartHelper.Result.FAILED ->
                        "Couldn't open settings. Go to Settings > Apps > Align and allow background activity."
                }
                if (message != null) Toast.makeText(ctx, message, Toast.LENGTH_LONG).show()
            },
            secondaryLabel = "I've done it",
            onSecondary = {
                store.autoStartConfirmed = true
                autoDone = true
            },
            extraText = family.steps,
            extraChip = family.title
        )
    )

    // The first step that is neither granted nor skipped is the one being asked right now.
    val current = steps.indices.firstOrNull { i -> !steps[i].granted && (skipped and (1 shl i)) == 0 } ?: -1
    val requiredMissing = !granted.usage || !granted.overlay

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(10.dp))
        Text(
            "Let's set things up",
            fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont, color = FG.Ink
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Align needs a few permissions to protect your focus. We'll ask for them one at a time.",
            fontSize = 14.sp, lineHeight = 20.sp, color = FG.TextSecondary
        )
        Spacer(Modifier.height(18.dp))

        steps.forEachIndexed { i, step ->
            val state = when {
                step.granted -> 2
                i == current -> 1
                (skipped and (1 shl i)) != 0 -> 3
                else -> 0
            }
            PermStepRow(step, state)
            if (i < steps.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(1.dp).background(FG.Track))
        }

        AnimatedVisibility(
            visible = requiredMissing && current == -1,
            enter = expandVertically(tween(300, easing = Ease)) + fadeIn(tween(300)),
            exit = shrinkVertically(tween(250, easing = Ease)) + fadeOut(tween(200))
        ) {
            Text(
                "Blocking won't work until Usage access and Display over other apps are allowed. You can do it later from Home.",
                fontSize = 12.sp, lineHeight = 17.sp, color = FG.Red,
                modifier = Modifier.padding(top = 16.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** state: 0 coming up, 1 asking now, 2 done, 3 skipped. */
@Composable
private fun PermStepRow(step: PermStep, state: Int) {
    val circle by animateColorAsState(
        when (state) {
            2 -> FG.Green
            1 -> FG.Primary
            else -> FG.Track
        },
        tween(350), label = "stepCircle"
    )
    val iconTint by animateColorAsState(
        if (state == 1 || state == 2) FG.White else FG.TextSecondary, tween(350), label = "stepIcon"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .animateContentSize(tween(320, easing = Ease))
            .padding(vertical = 14.dp)
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(circle),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(if (state == 2) R.drawable.ph_check_circle else step.icon),
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    step.title,
                    fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont,
                    color = if (state == 0 || state == 3) FG.TextSecondary else FG.Ink
                )
                if (state == 2) {
                    Spacer(Modifier.width(8.dp))
                    Chip("ALLOWED", FG.Green, FG.White)
                } else if (state == 3) {
                    Spacer(Modifier.width(8.dp))
                    Chip("SKIPPED", FG.Strong, FG.OnStrong)
                }
            }
            AnimatedVisibility(
                visible = state == 1,
                enter = expandVertically(tween(320, easing = Ease)) + fadeIn(tween(320)),
                exit = shrinkVertically(tween(260, easing = Ease)) + fadeOut(tween(180))
            ) {
                Column {
                    Spacer(Modifier.height(6.dp))
                    Text(step.why, fontSize = 13.sp, lineHeight = 19.sp, color = FG.TextSecondary)
                    if (step.extraChip != null) {
                        Spacer(Modifier.height(10.dp))
                        Chip(step.extraChip, FG.Strong, FG.OnStrong)
                    }
                    if (step.extraText != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(step.extraText, fontSize = 12.sp, lineHeight = 17.sp, color = FG.TextSecondary)
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FgButton(step.primaryLabel, step.onPrimary, Modifier.weight(1f), height = 46.dp)
                        FgButton(
                            step.secondaryLabel, step.onSecondary, Modifier.weight(1f),
                            style = FgButtonStyle.Light, height = 46.dp
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Page 3: closing note
// ---------------------------------------------------------------------------------------------

@Composable
private fun FinalPage() {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Illustration("meditating", Modifier.fillMaxWidth().height(200.dp))
        Spacer(Modifier.height(10.dp))
        Chip("YOU'RE READY", FG.Green, FG.White)
        Spacer(Modifier.height(12.dp))
        Text(
            "You're all set.",
            fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont, color = FG.Ink
        )
        Spacer(Modifier.height(14.dp))
        Text(
            "Start small. Pick the one or two apps that drain you most, give each a daily limit, and try a " +
                "25-minute focus session.",
            fontSize = 15.sp, lineHeight = 22.sp, color = FG.TextSecondary
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "When Align stops you, it isn't a punishment. It's a pause, a moment to choose what you do next " +
                "instead of scrolling on autopilot.",
            fontSize = 15.sp, lineHeight = 22.sp, color = FG.TextSecondary
        )
        Spacer(Modifier.height(18.dp))
        Text(
            "Your attention is yours. Take it back.",
            fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont, color = FG.Primary
        )
        Spacer(Modifier.height(6.dp))
        Text("Rohan Dora", fontSize = 13.sp, color = FG.TextSecondary)
        Spacer(Modifier.height(12.dp))
    }
}
