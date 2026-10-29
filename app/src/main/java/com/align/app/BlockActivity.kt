package com.align.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.align.app.data.AppHelper
import com.align.app.ui.AlignTheme

/**
 * Full-screen "time is up" screen shown on top of the limited app.
 * Back button and the Close button both lead to this app's home screen.
 */
class BlockActivity : ComponentActivity() {

    private var pkg by mutableStateOf("")
    private var usedMs by mutableStateOf(0L)
    private var limitMin by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readIntent(intent)

        // Immersive full screen.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    closeAndOpenApp()
                }
            }
        )

        setContent {
            AlignTheme {
                BlockScreen(
                    pkg = pkg,
                    usedMs = usedMs,
                    limitMin = limitMin,
                    onClose = { closeAndOpenApp() }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(i: Intent?) {
        pkg = i?.getStringExtra(EXTRA_PKG) ?: ""
        usedMs = i?.getLongExtra(EXTRA_USED_MS, 0L) ?: 0L
        limitMin = i?.getIntExtra(EXTRA_LIMIT_MIN, 0) ?: 0
    }

    /** "Close" -> take the user to our own app, away from the limited app. */
    private fun closeAndOpenApp() {
        val home = Intent(this, MainActivity::class.java).addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        )
        startActivity(home)
        finish()
    }

    companion object {
        const val EXTRA_PKG = "pkg"
        const val EXTRA_USED_MS = "used_ms"
        const val EXTRA_LIMIT_MIN = "limit_min"
    }
}

@Composable
private fun BlockScreen(pkg: String, usedMs: Long, limitMin: Int, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val label = remember(pkg) { if (pkg.isEmpty()) "this app" else AppHelper.label(ctx, pkg) }
    val icon = remember(pkg) { if (pkg.isEmpty()) null else AppHelper.icon(ctx, pkg, 192) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B1220), Color(0xFF5B1423)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(96.dp))
                Spacer(Modifier.height(24.dp))
            }
            Text(
                "Time's up",
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "You've used $label for ${AppHelper.formatMs(usedMs)} today.\n" +
                    "Your daily limit is ${AppHelper.formatMinutes(limitMin)}.",
                color = Color(0xFFE5E7EB),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(40.dp))
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF0B1220)
                )
            ) {
                Text("Close", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
