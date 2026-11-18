package com.example.audio

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

class TactileHaptics(
    private val context: Context,
    private val composeHaptic: HapticFeedback
) {
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private var lastTickMs: Long = 0L

    /**
     * Subtle mechanical tick when crossing a frequency/gain detent step.
     */
    fun tick(isCenterSnap: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        val minIntervalMs = if (isCenterSnap) 18L else 28L
        if (now - lastTickMs < minIntervalMs) return
        lastTickMs = now

        val vib = vibrator
        if (vib != null && vib.hasVibrator()) {
            try {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        val effectId = if (isCenterSnap) {
                            VibrationEffect.EFFECT_CLICK
                        } else {
                            VibrationEffect.EFFECT_TICK
                        }
                        vib.vibrate(VibrationEffect.createPredefined(effectId))
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                        val duration = if (isCenterSnap) 14L else 8L
                        val amplitude = if (vib.hasAmplitudeControl()) {
                            if (isCenterSnap) 110 else 55
                        } else {
                            VibrationEffect.DEFAULT_AMPLITUDE
                        }
                        vib.vibrate(VibrationEffect.createOneShot(duration, amplitude))
                    }
                    else -> {
                        @Suppress("DEPRECATION")
                        vib.vibrate(if (isCenterSnap) 14L else 8L)
                    }
                }
                return
            } catch (_: Exception) {
                // Fall back to Compose HapticFeedback below
            }
        }

        try {
            composeHaptic.performHapticFeedback(
                if (isCenterSnap) HapticFeedbackType.LongPress else HapticFeedbackType.TextHandleMove
            )
        } catch (_: Exception) {
        }
    }
}

@Composable
fun rememberTactileHaptics(): TactileHaptics {
    val context = LocalContext.current
    val composeHaptic = LocalHapticFeedback.current
    return remember(context, composeHaptic) {
        TactileHaptics(context, composeHaptic)
    }
}
