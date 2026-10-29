package com.align.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/**
 * Text whose digits roll like an odometer when the value changes: a bigger digit slides in from below,
 * a smaller one from above, the old digit leaves the other way and everything is clipped to its own cell.
 * Characters are matched from the right, so "95" -> "100" still lines the digits up. Non-digits (":", " min")
 * simply stay put. Tabular figures keep the width stable while digits swap.
 */
@Composable
fun AnimatedNumber(
    text: String,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    color: Color = FG.Ink,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    fontFamily: FontFamily? = BoldFont,
    durationMs: Int = 320
) {
    val ease = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val chars = text.toList()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        chars.forEachIndexed { index, ch ->
            key(chars.size - 1 - index) {
                AnimatedContent(
                    targetState = ch,
                    transitionSpec = {
                        val up = targetState > initialState
                        (slideInVertically(tween(durationMs, easing = ease)) { h -> if (up) h / 2 else -h / 2 } +
                            fadeIn(tween(durationMs, easing = ease)))
                            .togetherWith(
                                slideOutVertically(tween(durationMs, easing = ease)) { h -> if (up) -h / 2 else h / 2 } +
                                    fadeOut(tween(durationMs / 2, easing = ease))
                            )
                    },
                    label = "digit"
                ) { c ->
                    Text(
                        c.toString(),
                        fontSize = fontSize,
                        color = color,
                        fontWeight = fontWeight,
                        fontFamily = fontFamily,
                        style = TextStyle(fontFeatureSettings = "tnum")
                    )
                }
            }
        }
    }
}
