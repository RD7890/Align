package com.align.app.service

import android.animation.Animator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.align.app.MainActivity
import com.align.app.R
import com.align.app.data.AppHelper
import com.align.app.data.Illustrations

/** Texts of the block screen: daily limit ("Time's up") or focus session ("Stay focused"). */
class OverlayContent(
    val title: String,
    val message: String,
    val caption1: String,
    val value1: String,
    val caption2: String,
    val value2: String
)

/**
 * Full-screen "Time's up" overlay drawn directly with WindowManager (TYPE_APPLICATION_OVERLAY).
 * Unlike startActivity() from a background service, this is not subject to background-activity-start
 * restrictions or OEM "pop-up while in background" blocking - it only needs "Display over other apps".
 *
 * Look: red (#FF2541 shades) backdrop, white sheet that slides up from the bottom with the
 * downloaded unDraw illustration, staggered content and a bouncy Close button.
 *
 * All methods must be called on the main thread.
 */
class BlockOverlay(
    private val context: Context,
    private val onClosed: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private var root: View? = null
    private var sheet: View? = null
    private var staggerViews: List<View> = emptyList()
    private var illustrationImage: View? = null
    private val loops = ArrayList<Animator>()

    /** Custom bold font (res/font/bold_font.otf), same as the app UI. */
    private val boldTypeface: Typeface by lazy {
        ResourcesCompat.getFont(context, R.font.bold_font) ?: Typeface.DEFAULT_BOLD
    }

    private var currentPkg: String? = null
    private var closing = false
    private var notified = false

    @Volatile
    var isShowing: Boolean = false
        private set

    /** Daily limit reached. */
    fun show(pkg: String, usedMs: Long, limitMin: Int) {
        val label = AppHelper.label(context, pkg)
        show(
            pkg,
            OverlayContent(
                title = "Time's up",
                message = "You've reached today's limit for $label. Take a break - you've earned it.",
                caption1 = "Used today", value1 = AppHelper.formatMs(usedMs),
                caption2 = "Daily limit", value2 = AppHelper.formatMinutes(limitMin)
            )
        )
    }

    fun show(pkg: String, content: OverlayContent) {
        if (root != null) return
        try {
            closing = false
            notified = false
            currentPkg = pkg
            val view = buildView(pkg, content)
            windowManager.addView(view, layoutParams())
            view.requestFocus()
            root = view
            isShowing = true
            playEnter()
        } catch (e: Exception) {
            // Most likely "Display over other apps" was revoked.
            Log.e(TAG, "Could not show overlay", e)
            root = null
            sheet = null
            isShowing = false
        }
    }

    /** Removes the overlay immediately (no animation). Safe to call repeatedly. */
    fun hide() {
        for (a in loops) a.cancel()
        loops.clear()
        root?.animate()?.cancel()
        sheet?.animate()?.cancel()
        root?.let {
            try {
                windowManager.removeViewImmediate(it)
            } catch (e: Exception) {
                Log.w(TAG, "removeView failed", e)
            }
        }
        root = null
        sheet = null
        isShowing = false
    }

    /**
     * Close button / Back.
     *
     * Order matters: our own app is started while the overlay window is still on screen. Android
     * only lets an app start an activity "from the background" when it has a visible window, and
     * a visible overlay counts. (Removing the overlay first, as before, made the system silently
     * drop the launch, so the limited app simply came back.) After the slide-down animation the
     * limited app's process is also killed so it is really closed, not just hidden.
     */
    private fun close() {
        if (closing) return
        closing = true
        val pkg = currentPkg

        launchOwnApp()

        val screenH = context.resources.displayMetrics.heightPixels.toFloat()
        sheet?.animate()
            ?.translationY(screenH)
            ?.setDuration(SLIDE_OUT_MS)
            ?.setInterpolator(AccelerateInterpolator(1.6f))
            ?.start()
        root?.animate()
            ?.alpha(0f)
            ?.setStartDelay(140)
            ?.setDuration(SLIDE_OUT_MS - 140)
            ?.start()

        handler.postDelayed({
            hide()
            notifyClosed()
        }, SLIDE_OUT_MS + 40)

        if (pkg != null) {
            handler.postDelayed({ killApp(pkg) }, KILL_DELAY_MS)
        }
    }

    private fun notifyClosed() {
        if (notified) return
        notified = true
        onClosed()
    }

    private fun launchOwnApp() {
        val intent = Intent(context, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not open app", e)
        }
        // Belt and braces: a PendingIntent that explicitly opts in to background activity start (API 34+).
        try {
            val pi = PendingIntent.getActivity(
                context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            if (Build.VERSION.SDK_INT >= 34) {
                val options = ActivityOptions.makeBasic()
                options.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                pi.send(options.toBundle())
            } else {
                pi.send()
            }
        } catch (e: Exception) {
            Log.w(TAG, "PendingIntent launch failed", e)
        }
    }

    private fun killApp(pkg: String) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.killBackgroundProcesses(pkg)
        } catch (e: Exception) {
            Log.w(TAG, "Could not close $pkg", e)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Animations
    // ---------------------------------------------------------------------------------------

    private fun playEnter() {
        val r = root ?: return
        val s = sheet ?: return

        r.animate().alpha(1f).setDuration(340).start()
        s.animate()
            .translationY(0f)
            .setDuration(580)
            .setInterpolator(DecelerateInterpolator(2.4f))
            .start()

        staggerViews.forEachIndexed { i, v ->
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(260L + i * 80L)
                .setDuration(460)
                .setInterpolator(DecelerateInterpolator(2f))
                .start()
        }

        // Illustration floats gently up and down while the screen is open.
        illustrationImage?.let { img ->
            val bob = ObjectAnimator.ofPropertyValuesHolder(
                img,
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, dp(6).toFloat()),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.02f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.02f)
            ).apply {
                duration = 2400
                startDelay = 500
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            }
            loops.add(bob)
            bob.start()
        }
    }

    /** Button feedback: shrinks while pressed, springs back with a little overshoot. */
    @Suppress("ClickableViewAccessibility")
    private fun View.bouncyPress() {
        setOnTouchListener { v, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.94f).scaleY(0.94f).setDuration(90).setInterpolator(null).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    v.animate().scaleX(1f).scaleY(1f).setDuration(260)
                        .setInterpolator(OvershootInterpolator(3f)).start()
            }
            false // let the click listener still fire
        }
    }

    // ---------------------------------------------------------------------------------------
    // Window + views
    // ---------------------------------------------------------------------------------------

    @Suppress("DEPRECATION")
    private fun layoutParams(): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= 26) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
        }
        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        )
        if (Build.VERSION.SDK_INT >= 28) {
            params.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        return params
    }

    @Suppress("DEPRECATION")
    private fun buildView(pkg: String, content: OverlayContent): View {
        val label = AppHelper.label(context, pkg)
        val screenH = context.resources.displayMetrics.heightPixels.toFloat()

        val rootView = OverlayRoot(context) { close() }
        rootView.isFocusableInTouchMode = true
        rootView.isClickable = true // swallow touches so the app underneath can't be used
        rootView.alpha = 0f
        rootView.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(COLOR_PRIMARY_LIGHT, COLOR_PRIMARY, COLOR_PRIMARY_DEEP)
        )
        rootView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        val sheetView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(12), dp(24), dp(40))
            val r = dp(34).toFloat()
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
            }
            translationY = screenH // starts below the screen, slides up in playEnter()
        }

        // Grab handle
        sheetView.addView(View(context).apply {
            background = rounded(COLOR_TRACK, dp(3))
        }, LinearLayout.LayoutParams(dp(44), dp(5)))

        // Illustration (downloaded unDraw SVG, accent colour #FF2541)
        val illustrationWrap = FrameLayout(context)
        val illustration = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            Illustrations.render(context, "time-management", 800)?.let { setImageBitmap(it) }
        }
        // 14dp margin around the picture: the float (0..6dp) and the 2% breathing never reach the
        // edge of the wrapper, so nothing is clipped.
        illustrationWrap.addView(
            illustration,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins(dp(14), dp(14), dp(14), dp(14))
            }
        )
        sheetView.addView(
            illustrationWrap,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(220)).apply { topMargin = dp(14) }
        )
        illustrationImage = illustration

        // App chip
        val chip = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(16), dp(6))
            background = rounded(COLOR_SURFACE, dp(30))
        }
        try {
            val icon = ImageView(context)
            icon.setImageDrawable(context.packageManager.getApplicationIcon(pkg))
            chip.addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)))
        } catch (e: Exception) {
            // no icon - fine
        }
        chip.addView(TextView(context).apply {
            text = label
            setTextColor(COLOR_BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = boldTypeface
            maxLines = 1
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = dp(8) })
        sheetView.addView(
            chip,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        // Title + message
        val title = TextView(context).apply {
            text = content.title
            setTextColor(COLOR_BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            typeface = boldTypeface
            gravity = Gravity.CENTER
        }
        sheetView.addView(
            title,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(14) }
        )
        val message = TextView(context).apply {
            text = content.message
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
        }
        sheetView.addView(
            message,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )

        // Used / limit tiles
        val stats = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(
            statTile(content.caption1, content.value1, COLOR_PRIMARY),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) }
        )
        stats.addView(
            statTile(content.caption2, content.value2, COLOR_BLACK),
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) }
        )
        sheetView.addView(
            stats,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(20) }
        )

        // Close button (flat #FF2541, bouncy press)
        val closeButton = TextView(context).apply {
            text = "Close"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = boldTypeface
            gravity = Gravity.CENTER
            isClickable = true
            background = rounded(COLOR_PRIMARY, dp(20))
            setOnClickListener { close() }
            bouncyPress()
        }
        sheetView.addView(
            closeButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(58)).apply { topMargin = dp(24) }
        )

        // Everything except the handle fades/slides in one after another.
        val items = listOf<View>(illustrationWrap, chip, title, message, stats, closeButton)
        items.forEach {
            it.alpha = 0f
            it.translationY = dp(36).toFloat()
        }
        staggerViews = items

        rootView.addView(
            sheetView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )
        sheet = sheetView
        return rootView
    }

    private fun statTile(caption: String, value: String, valueColor: Int): View {
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(14), dp(12), dp(14))
            background = rounded(COLOR_SURFACE, dp(20))
        }
        tile.addView(TextView(context).apply {
            text = caption
            setTextColor(COLOR_TEXT_SECONDARY)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
        })
        tile.addView(TextView(context).apply {
            text = value
            setTextColor(valueColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = boldTypeface
            gravity = Gravity.CENTER
        })
        return tile
    }

    private fun rounded(color: Int, radiusPx: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusPx.toFloat()
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    /** FrameLayout that turns the Back key into "Close". */
    private class OverlayRoot(context: Context, private val onBack: () -> Unit) : FrameLayout(context) {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) onBack()
                return true
            }
            return super.dispatchKeyEvent(event)
        }
    }

    private companion object {
        const val TAG = "BlockOverlay"
        const val SLIDE_OUT_MS = 420L
        const val KILL_DELAY_MS = 1100L

        val COLOR_PRIMARY_LIGHT = Color.parseColor("#FF5C73")
        val COLOR_PRIMARY = Color.parseColor("#FF2541")
        val COLOR_PRIMARY_DEEP = Color.parseColor("#8E0B20")
        val COLOR_BLACK = Color.parseColor("#0B0B0D")
        val COLOR_SURFACE = Color.parseColor("#F6F6F8")
        val COLOR_TRACK = Color.parseColor("#E0E0E6")
        val COLOR_TEXT_SECONDARY = Color.parseColor("#6E6E78")
    }
}
