package com.align.app.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

class AppEntry(val pkg: String, val label: String)

object AppHelper {

    /** Launcher activities of all installed apps except this one (no labels / icons loaded - cheap). */
    fun launchableResolveInfos(context: Context): List<android.content.pm.ResolveInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return infos
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
    }

    /** All apps with a launcher icon, except this app, sorted A-Z. */
    fun launchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        return launchableResolveInfos(context)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .sortedBy { it.label.lowercase() }
    }

    class AppLite(val pkg: String, val label: String, val category: Category)

    /** Apps with label + category, A-Z. Labels are loaded once and cached; icons are NOT loaded here. */
    fun launchableLite(context: Context): List<AppLite> {
        val pm = context.packageManager
        return launchableResolveInfos(context).map {
            val pkg = it.activityInfo.packageName
            val label = AppCache.cachedLabel(pkg) ?: it.loadLabel(pm).toString().also { l -> AppCache.putLabel(pkg, l) }
            AppLite(pkg, label, categoryOf(it.activityInfo.applicationInfo))
        }.sortedBy { it.label.lowercase() }
    }

    enum class Category(val title: String) {
        SOCIAL("Social"), VIDEO("Video"), GAMES("Games"), NEWS("News"), MUSIC("Music"),
        PRODUCTIVITY("Productivity"), OTHER("Other")
    }

    /** Android's own app category (API 26+) mapped to the groups shown in the blocked-apps screen. */
    fun categoryOf(context: Context, pkg: String): Category = try {
        categoryOf(context.packageManager.getApplicationInfo(pkg, 0))
    } catch (e: Exception) {
        Category.OTHER
    }

    fun categoryOf(info: android.content.pm.ApplicationInfo): Category = try {
        val pkg = info.packageName
        when (info.category) {
            android.content.pm.ApplicationInfo.CATEGORY_SOCIAL -> Category.SOCIAL
            android.content.pm.ApplicationInfo.CATEGORY_VIDEO -> Category.VIDEO
            android.content.pm.ApplicationInfo.CATEGORY_GAME -> Category.GAMES
            android.content.pm.ApplicationInfo.CATEGORY_NEWS -> Category.NEWS
            android.content.pm.ApplicationInfo.CATEGORY_AUDIO -> Category.MUSIC
            android.content.pm.ApplicationInfo.CATEGORY_PRODUCTIVITY -> Category.PRODUCTIVITY
            else -> if (pkg in KNOWN_DISTRACTIONS) Category.SOCIAL else Category.OTHER
        }
    } catch (e: Exception) {
        Category.OTHER
    }

    /** Apps that usually distract: social, video, games, news - plus a list of well-known ones. */
    fun suggestedBlocked(context: Context): Set<String> {
        val wanted = setOf(Category.SOCIAL, Category.VIDEO, Category.GAMES, Category.NEWS)
        return launchableResolveInfos(context)
            .filter {
                val pkg = it.activityInfo.packageName
                pkg in KNOWN_DISTRACTIONS || categoryOf(it.activityInfo.applicationInfo) in wanted
            }
            .map { it.activityInfo.packageName }
            .toSet()
    }

    private val KNOWN_DISTRACTIONS = setOf(
        "com.instagram.android", "com.facebook.katana", "com.facebook.lite", "com.snapchat.android",
        "com.twitter.android", "com.zhiliaoapp.musically", "com.ss.android.ugc.trill",
        "com.google.android.youtube", "com.reddit.frontpage", "com.pinterest", "com.netflix.mediaclient",
        "in.mohalla.sharechat", "com.roposo.android", "tv.twitch.android.app", "com.discord"
    )

    @Volatile
    private var statsPackagesCache: Pair<Long, Set<String>>? = null

    /**
     * Packages that should appear in usage charts: launchable apps, minus this app and the home launcher.
     * Only package names are needed (no label loading) and the result is reused for a minute, because the
     * dashboard asks for it on every stats refresh.
     */
    fun statsPackages(context: Context): Set<String> {
        val now = System.currentTimeMillis()
        statsPackagesCache?.let { (at, set) -> if (now - at < 60_000L) return set }
        val pm = context.packageManager
        val home = pm.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val infos = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        val result = infos
            .map { it.activityInfo.packageName }
            .filter { it != context.packageName && it != home }
            .toSet()
        statsPackagesCache = now to result
        return result
    }

    fun label(context: Context, pkg: String): String {
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (e: Exception) {
            pkg
        }
    }

    fun icon(context: Context, pkg: String, sizePx: Int = 96): ImageBitmap? {
        return try {
            context.packageManager.getApplicationIcon(pkg).toBitmap(sizePx, sizePx).asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }

    fun formatMs(ms: Long): String = formatMinutes((ms / 60_000L).toInt())

    fun formatMinutes(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "${h}h ${m}m"
            h > 0 -> "${h}h"
            else -> "${m}m"
        }
    }
}
