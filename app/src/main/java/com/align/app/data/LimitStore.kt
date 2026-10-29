package com.align.app.data

import android.content.Context
import org.json.JSONObject

/**
 * Pomodoro cycle. [phase]: 0 idle, 1 focus, 2 short break, 3 long break. [round] is 1-based.
 * [end] = epoch millis when the phase ends; 0 while a phase is waiting for the user to start it.
 * The remaining fields are the user's settings.
 */
data class PomoState(
    val phase: Int = 0,
    val round: Int = 0,
    val end: Long = 0L,
    val focusMin: Int = 25,
    val breakMin: Int = 5,
    val longMin: Int = 15,
    val rounds: Int = 4,
    val auto: Boolean = true
)

/** Tiny persistence layer: package name -> daily limit in minutes, plus the on/off switch. */
class LimitStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(): Map<String, Int> {
        val raw = prefs.getString(KEY_LIMITS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(raw)
            val result = HashMap<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = obj.getInt(k)
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun set(pkg: String, minutes: Int) {
        val map = all().toMutableMap()
        map[pkg] = minutes
        save(map)
    }

    fun remove(pkg: String) {
        val map = all().toMutableMap()
        map.remove(pkg)
        save(map)
    }

    var monitoring: Boolean
        get() = prefs.getBoolean(KEY_MONITORING, true)
        set(value) {
            prefs.edit().putBoolean(KEY_MONITORING, value).apply()
        }

    /** User confirmed they allowed auto-start / background activity (Android can't tell us). */
    var autoStartConfirmed: Boolean
        get() = prefs.getBoolean(KEY_AUTOSTART_DONE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTOSTART_DONE, value).apply()
        }

    /** The one-time "allow auto-start" dialog was already shown. */
    var autoStartPrompted: Boolean
        get() = prefs.getBoolean(KEY_AUTOSTART_PROMPTED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_AUTOSTART_PROMPTED, value).apply()
        }

    // ---- focus timer ----

    /** Epoch millis when the running focus session ends; 0 = no session. */
    var focusEndAt: Long
        get() = prefs.getLong(KEY_FOCUS_END, 0L)
        set(value) {
            prefs.edit().putLong(KEY_FOCUS_END, value).apply()
        }

    var focusDurationMin: Int
        get() = prefs.getInt(KEY_FOCUS_MIN, 25)
        set(value) {
            prefs.edit().putInt(KEY_FOCUS_MIN, value).apply()
        }

    fun focusActive(): Boolean = focusEndAt > System.currentTimeMillis()

    // ---- pomodoro ----

    /** Which mode the Home tab shows: 0 = plain timer, 1 = pomodoro. */
    var focusMode: Int
        get() = prefs.getInt(KEY_FOCUS_MODE, 0)
        set(value) {
            prefs.edit().putInt(KEY_FOCUS_MODE, value).apply()
        }

    val pomoPhase: Int get() = prefs.getInt(KEY_POMO_PHASE, 0)

    fun pomo(): PomoState = PomoState(
        phase = prefs.getInt(KEY_POMO_PHASE, 0),
        round = prefs.getInt(KEY_POMO_ROUND, 0),
        end = prefs.getLong(KEY_POMO_END, 0L),
        focusMin = prefs.getInt(KEY_POMO_FOCUS, 25),
        breakMin = prefs.getInt(KEY_POMO_BREAK, 5),
        longMin = prefs.getInt(KEY_POMO_LONG, 15),
        rounds = prefs.getInt(KEY_POMO_ROUNDS, 4),
        auto = prefs.getBoolean(KEY_POMO_AUTO, true)
    )

    /** Saves only the settings part of [s]. */
    fun setPomoConfig(s: PomoState) {
        prefs.edit()
            .putInt(KEY_POMO_FOCUS, s.focusMin)
            .putInt(KEY_POMO_BREAK, s.breakMin)
            .putInt(KEY_POMO_LONG, s.longMin)
            .putInt(KEY_POMO_ROUNDS, s.rounds)
            .putBoolean(KEY_POMO_AUTO, s.auto)
            .apply()
    }

    /** Saves only the running part (phase / round / end). */
    fun setPomoRun(phase: Int, round: Int, end: Long) {
        prefs.edit()
            .putInt(KEY_POMO_PHASE, phase)
            .putInt(KEY_POMO_ROUND, round)
            .putLong(KEY_POMO_END, end)
            .apply()
    }

    /** Apps that are blocked while a focus session runs. */
    fun focusBlocked(): Set<String> = HashSet(prefs.getStringSet(KEY_FOCUS_BLOCKED, emptySet()) ?: emptySet())

    fun setFocusBlocked(pkgs: Set<String>) {
        prefs.edit().putStringSet(KEY_FOCUS_BLOCKED, HashSet(pkgs)).apply()
    }

    /** The default blocked list was filled in once (user changes are kept afterwards). */
    var focusInitialized: Boolean
        get() = prefs.getBoolean(KEY_FOCUS_INIT, false)
        set(value) {
            prefs.edit().putBoolean(KEY_FOCUS_INIT, value).apply()
        }

    /** 0 = follow system, 1 = light, 2 = dark. */
    var themeMode: Int
        get() = prefs.getInt(KEY_THEME, 0)
        set(value) {
            prefs.edit().putInt(KEY_THEME, value).apply()
        }

    private fun save(map: Map<String, Int>) {
        val obj = JSONObject()
        for ((k, v) in map) obj.put(k, v)
        prefs.edit().putString(KEY_LIMITS, obj.toString()).apply()
    }

    private companion object {
        const val PREFS = "align"
        const val KEY_LIMITS = "limits"
        const val KEY_MONITORING = "monitoring"
        const val KEY_FOCUS_END = "focus_end"
        const val KEY_FOCUS_MIN = "focus_min"
        const val KEY_FOCUS_BLOCKED = "focus_blocked"
        const val KEY_FOCUS_INIT = "focus_init"
        const val KEY_THEME = "theme_mode"
        const val KEY_FOCUS_MODE = "focus_mode"
        const val KEY_POMO_PHASE = "pomo_phase"
        const val KEY_POMO_ROUND = "pomo_round"
        const val KEY_POMO_END = "pomo_end"
        const val KEY_POMO_FOCUS = "pomo_focus_min"
        const val KEY_POMO_BREAK = "pomo_break_min"
        const val KEY_POMO_LONG = "pomo_long_min"
        const val KEY_POMO_ROUNDS = "pomo_rounds"
        const val KEY_POMO_AUTO = "pomo_auto"
        const val KEY_AUTOSTART_DONE = "autostart_done"
        const val KEY_AUTOSTART_PROMPTED = "autostart_prompted"
    }
}
