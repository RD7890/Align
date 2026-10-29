package com.align.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.align.app.data.AppCache
import com.align.app.data.AppHelper
import com.align.app.usage.UsageHelper.UsageStats

/** Everything we know about one app: usage, limit, charts. */
@Composable
fun AppDetailScreen(
    pkg: String,
    limitMin: Int?,
    stats: UsageStats?,
    locked: Boolean = false,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onRemove: () -> Unit
) {
    val ctx = LocalContext.current
    val label = remember(pkg) { AppCache.label(ctx, pkg) }

    val week = stats?.weekFor(pkg) ?: List(7) { 0L }
    val hours = stats?.hoursFor(pkg) ?: LongArray(24)
    val todayMs = week.last()
    val limitMs = limitMin?.let { it * 60_000L }
    val over = limitMs != null && todayMs >= limitMs
    val fraction = if (limitMs != null && limitMs > 0) (todayMs.toFloat() / limitMs).coerceIn(0f, 1f) else 0f

    val labels = remember { weekLabels() }
    val weekTotal = week.sum()
    val weekAvg = weekTotal / week.size
    val busiestIdx = week.indices.maxByOrNull { week[it] } ?: 0
    val peakHour = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0L }
    val daysOver = if (limitMs != null) week.count { it >= limitMs } else null

    val statusText = when {
        limitMin == null -> "No limit set"
        over -> "Limit reached"
        else -> "On track"
    }
    val heroIllustration = when {
        limitMin == null -> "screen-time"
        over -> "alarm-clock"
        else -> "well-done"
    }

    Surface(Modifier.fillMaxSize(), color = FG.Bg) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 32.dp)
        ) {
            item(key = "top") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FgRoundButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FG.OnStrong)
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        label,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            item(key = "hero") {
                Column(
                    Modifier
                        .staggerIn(0)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(30.dp))
                        .background(FG.HeroBrush)
                        .padding(20.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(FG.White)
                                .padding(7.dp),
                            contentAlignment = Alignment.Center
                        ) { AppIconImage(pkg, 44.dp) }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                label,
                                color = FG.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (limitMin != null) "Daily limit ${AppHelper.formatMinutes(limitMin)}" else "Tracking only",
                                color = FG.White.copy(alpha = 0.85f),
                                fontSize = 13.sp
                            )
                        }
                        Chip(
                            statusText.uppercase(),
                            when {
                                limitMin == null -> FG.Strong
                                over -> FG.Red
                                else -> FG.Green
                            },
                            FG.White
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RingProgress(
                            fraction = fraction,
                            modifier = Modifier.size(144.dp),
                            trackColor = FG.White.copy(alpha = 0.28f),
                            ringBrush = Brush.linearGradient(listOf(FG.White, FG.White)),
                            strokeWidth = 12.dp
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    shortDuration(todayMs),
                                    color = FG.White,
                                    fontSize = 26.sp,
                                    fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont
                                )
                                Text(
                                    if (limitMin != null) "of ${AppHelper.formatMinutes(limitMin)}" else "today",
                                    color = FG.White.copy(alpha = 0.85f),
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Box(
                            Modifier
                                .weight(1f)
                                .height(144.dp)
                                .clip(RoundedCornerShape(24.dp))
                                .background(FG.White)
                                .padding(6.dp)
                        ) { Illustration(heroIllustration, Modifier.fillMaxSize()) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item(key = "tiles") {
                val remaining: String
                val remainingTitle: String
                if (limitMs == null) {
                    remainingTitle = "Remaining"
                    remaining = "No limit"
                } else if (over) {
                    remainingTitle = "Over by"
                    remaining = shortDuration(todayMs - limitMs)
                } else {
                    remainingTitle = "Remaining"
                    remaining = shortDuration(limitMs - todayMs)
                }
                Column(Modifier.staggerIn(1), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Used today", shortDuration(todayMs), Modifier.weight(1f))
                        StatTile(
                            "Daily limit",
                            if (limitMin != null) AppHelper.formatMinutes(limitMin) else "Not set",
                            Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(remainingTitle, remaining, Modifier.weight(1f), highlight = over)
                        StatTile("7-day average", shortDuration(weekAvg), Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("7-day total", shortDuration(weekTotal), Modifier.weight(1f))
                        StatTile(
                            "Busiest day",
                            if (week[busiestIdx] > 0L) "${labels[busiestIdx]} · ${shortDuration(week[busiestIdx])}" else "—",
                            Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            "Days over limit",
                            if (daysOver != null) "$daysOver of ${week.size}" else "—",
                            Modifier.weight(1f),
                            highlight = daysOver != null && daysOver > 0
                        )
                        StatTile(
                            "Peak hour today",
                            if (peakHour != null) hourLabel(peakHour) else "—",
                            Modifier.weight(1f)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item(key = "week") {
                SectionCard(Modifier.staggerIn(2)) {
                    Text("Last 7 days", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                    Text(
                        if (limitMin != null) "Dashed line = your daily limit" else "Daily usage of this app",
                        fontSize = 12.sp,
                        color = FG.TextSecondary
                    )
                    Spacer(Modifier.height(16.dp))
                    BarChart(values = week, labels = labels, limitMs = limitMs)
                }
                Spacer(Modifier.height(16.dp))
            }

            item(key = "hours") {
                SectionCard(Modifier.staggerIn(3)) {
                    Text("Today by hour", fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = BoldFont)
                    Text("When you opened it today", fontSize = 12.sp, color = FG.TextSecondary)
                    Spacer(Modifier.height(16.dp))
                    HourlyChart(hours)
                }
                Spacer(Modifier.height(20.dp))
            }

            item(key = "actions") {
                Column(Modifier.navigationBarsPadding()) {
                    if (limitMin != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FgButton("Edit limit", onEdit, Modifier.weight(1f), enabled = !locked)
                            FgButton("Remove limit", onRemove, Modifier.weight(1f), style = FgButtonStyle.Outline, enabled = !locked)
                        }
                    } else {
                        FgButton("Set daily limit", onEdit, Modifier.fillMaxWidth(), enabled = !locked)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(title: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(FG.Surface)
            .padding(14.dp)
    ) {
        Text(title, fontSize = 12.sp, color = FG.TextSecondary, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold, fontFamily = BoldFont,
            color = if (highlight) FG.Red else FG.Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
