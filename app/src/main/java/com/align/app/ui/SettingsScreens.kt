package com.align.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.align.app.R
import com.align.app.data.AppCache
import com.align.app.data.AppHelper
import com.align.app.data.LimitStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------------------------
// Settings root
// ---------------------------------------------------------------------------------------------

fun LazyListScope.settingsRootItems(
    themeMode: Int,
    limitCount: Int,
    blockedCount: Int,
    locked: Boolean,
    onTheme: () -> Unit,
    onLimits: () -> Unit,
    onBlocked: () -> Unit
) {
    if (locked) {
        item(key = "locked") {
            Spacer(Modifier.height(8.dp))
            LockedBanner()
        }
    }
    item(key = "rows") {
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsRow(
                R.drawable.ph_palette, "Theme",
                when (themeMode) { 1 -> "Light"; 2 -> "Dark"; else -> "System default" },
                onTheme, Modifier.staggerIn(0)
            )
            SettingsRow(
                R.drawable.ph_hourglass_medium, "App limits",
                if (limitCount == 0) "Daily time limits per app" else "$limitCount apps limited",
                onLimits, Modifier.staggerIn(1)
            )
            SettingsRow(
                R.drawable.ph_prohibit, "Blocked apps",
                "$blockedCount apps blocked during focus timer",
                onBlocked, Modifier.staggerIn(2)
            )
        }
    }
}

@Composable
private fun SettingsRow(icon: Int, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .clip(RoundedCornerShape(22.dp))
            .background(FG.Surface)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(FG.Primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(painterResource(icon), null, tint = FG.Primary, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
            Text(subtitle, fontSize = 12.sp, color = FG.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(painterResource(R.drawable.ph_caret_right), null, tint = FG.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun LockedBanner(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FG.Primary.copy(alpha = 0.14f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(R.drawable.ph_lock_simple_fill), null, tint = FG.Primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            "Locked while a focus session is running.",
            fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont, color = FG.Primary
        )
    }
}

/** Full-screen sub page with back button and title (used for Theme / App limits / Blocked apps). */
@Composable
fun SettingsSubScreen(title: String, locked: Boolean, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
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
                Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont)
            }
            if (locked) LockedBanner(Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
            content()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Theme
// ---------------------------------------------------------------------------------------------

@Composable
fun ThemeScreen(mode: Int, locked: Boolean, onSelect: (Int) -> Unit, onBack: () -> Unit) {
    SettingsSubScreen("Theme", locked, onBack) {
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThemeOption(R.drawable.ph_moon, "Dark", "Easy on the eyes at night", mode == 2, !locked) { onSelect(2) }
            ThemeOption(R.drawable.ph_sun, "Light", "Bright and clean", mode == 1, !locked) { onSelect(1) }
            ThemeOption(R.drawable.ph_device_mobile, "System default", "Follows your phone setting", mode == 0, !locked) { onSelect(0) }
        }
    }
}

@Composable
private fun ThemeOption(icon: Int, title: String, subtitle: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val border by animateColorAsState(if (selected) FG.Primary else FG.Track, tween(220), label = "themeBorder")
    val shape = RoundedCornerShape(22.dp)
    val (originTracker, markOrigin) = rememberThemeOrigin()
    Row(
        Modifier
            .fillMaxWidth()
            .then(originTracker)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f }
            .pressable(enabled = enabled, onClick = {
                markOrigin()
                onClick()
            })
            .clip(shape)
            .background(FG.Surface)
            .border(2.dp, border, shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), null, tint = if (selected) FG.Primary else FG.Ink, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
            Text(subtitle, fontSize = 12.sp, color = FG.TextSecondary)
        }
        if (selected) {
            Icon(painterResource(R.drawable.ph_check_circle), null, tint = FG.Primary, modifier = Modifier.size(26.dp))
        } else {
            Box(Modifier.size(22.dp).border(2.dp, FG.Track, CircleShape))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Blocked apps (focus timer)
// ---------------------------------------------------------------------------------------------

private class AppItem(val pkg: String, val label: String, val category: AppHelper.Category)

@Composable
fun BlockedAppsScreen(locked: Boolean, onChanged: (Set<String>) -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { LimitStore(ctx) }
    var blocked by remember { mutableStateOf(store.focusBlocked()) }
    var filter by remember { mutableStateOf("All") }

    val apps by produceState<List<AppItem>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) {
            if (!store.focusInitialized) {
                store.setFocusBlocked(AppHelper.suggestedBlocked(ctx))
                store.focusInitialized = true
            }
            AppHelper.launchableLite(ctx).map { AppItem(it.pkg, it.label, it.category) }
        }
        blocked = store.focusBlocked()
        onChanged(blocked)
    }

    fun update(set: Set<String>) {
        blocked = set
        store.setFocusBlocked(set)
        onChanged(set)
    }

    SettingsSubScreen("Blocked apps", locked, onBack) {
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = FG.Primary)
            }
            return@SettingsSubScreen
        }
        val categories = list.map { it.category }.distinct().sortedBy { it.ordinal }
        val filters = listOf("All", "Blocked", "Allowed") + categories.map { it.title }
        val shown = when (filter) {
            "All" -> list
            "Blocked" -> list.filter { it.pkg in blocked }
            "Allowed" -> list.filter { it.pkg !in blocked }
            else -> list.filter { it.category.title == filter }
        }
        val blockedCount = list.count { it.pkg in blocked }

        Text(
            "$blockedCount of ${list.size} apps blocked while the timer runs. Everything else stays available.",
            fontSize = 13.sp, color = FG.TextSecondary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { f -> FilterPill(f, filter == f, { filter = f }) }
        }
        if (filter !in listOf("All", "Blocked", "Allowed")) {
            Row(
                Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FgButton(
                    "Block all", { update(blocked + shown.map { it.pkg }) },
                    Modifier.weight(1f), height = 42.dp, enabled = !locked
                )
                FgButton(
                    "Allow all", { update(blocked - shown.map { it.pkg }.toSet()) },
                    Modifier.weight(1f), style = FgButtonStyle.Light, height = 42.dp, enabled = !locked
                )
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 32.dp)
        ) {
            items(shown, key = { it.pkg }, contentType = { "app" }) { app ->
                val isBlocked = app.pkg in blocked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(FG.Surface)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIconImage(app.pkg, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            app.label, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, fontFamily = BoldFont,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(app.category.title, fontSize = 11.sp, color = FG.TextSecondary)
                    }
                    Switch(
                        checked = isBlocked,
                        onCheckedChange = { on -> update(if (on) blocked + app.pkg else blocked - app.pkg) },
                        enabled = !locked,
                        colors = fgSwitchColors()
                    )
                }
            }
        }
    }
}
