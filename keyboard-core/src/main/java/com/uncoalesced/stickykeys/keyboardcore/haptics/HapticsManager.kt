// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.haptics

import android.content.Context
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Full-scale amplitude for [VibrationEffect.createOneShot]. */
internal const val MAX_AMPLITUDE = 255

/**
 * The hard ceiling, as a share of the motor's full scale.
 *
 * The slider is a percentage of *this*, not of the motor: 100 on the slider is 80% power and
 * nothing ever reaches full scale. Full-amplitude one-shots at typing cadence are audible
 * across a quiet room and unpleasant to hold.
 */
internal const val POWER_CAP = 0.80f

/** Slider percent applied when the user has never touched the setting. */
internal const val DEFAULT_HAPTICS_PERCENT = 50

/**
 * Motor amplitude for a slider position.
 *
 * Deliberately linear with a cap and no easing: `(percent / 100) * 80` of full scale, exactly
 * as specified. Zero means silent -- not "very light" -- so the slider alone can turn haptics
 * off without also flipping the enable switch.
 *
 * Pure, so the curve is assertable without a motor: 0 -> 0, 50 -> 40% (102), 100 -> 80% (204).
 */
internal fun amplitudeFor(sliderPercent: Int): Int {
    val percent = sliderPercent.coerceIn(0, 100)
    return Math.round(MAX_AMPLITUDE * (percent / 100f) * POWER_CAP)
}

@Singleton
class HapticsManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val preferences: KeyboardPreferences,
    ) {
        private val vibrator: Vibrator? by lazy {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager =
                    context.getSystemService(
                        Context.VIBRATOR_MANAGER_SERVICE,
                    ) as? VibratorManager
                vibratorManager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }

        /** One tap: a key on the keyboard, or any button in the app. */
        fun performKeyPressHaptic() = vibrate(KEY_PRESS_MS, preferences.hapticsIntensity.value)

        /**
         * Sending a sticker: longer and softer than a keystroke, so a committed action does
         * not land in the hand as though it were a typo.
         */
        fun performStickerSendHaptic() =
            vibrate(
                STICKER_SEND_MS,
                (preferences.hapticsIntensity.value * STICKER_SEND_SCALE).toInt(),
            )

        private fun vibrate(
            durationMillis: Long,
            sliderPercent: Int,
        ) {
            if (!preferences.hapticsEnabled.value) return

            val amplitude = amplitudeFor(sliderPercent)
            // Zero amplitude is a documented IllegalArgumentException for createOneShot, and
            // a zero-power buzz is not a thing to ask the motor for in the first place.
            if (amplitude <= 0) return

            val v = vibrator ?: return
            if (!v.hasVibrator()) return

            val effect = VibrationEffect.createOneShot(durationMillis, amplitude)

            // The usage has to be declared, or the vibration is dropped before it reaches
            // the motor.
            //
            // An IME is a background service. From Android 12 the system filters
            // un-attributed vibrations from non-foreground processes, which is why nothing
            // was felt even once the keyboard was rendering. Tagging this as touch feedback
            // is what makes it survive that filter -- and it also makes the buzz obey the
            // user's own touch-feedback setting instead of impersonating an alarm.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                v.vibrate(
                    effect,
                    VibrationAttributes
                        .Builder()
                        .setUsage(VibrationAttributes.USAGE_TOUCH)
                        .build(),
                )
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(
                    effect,
                    AudioAttributes
                        .Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
            }
        }

        private companion object {
            const val KEY_PRESS_MS = 10L
            const val STICKER_SEND_MS = 20L
            const val STICKER_SEND_SCALE = 0.7f
        }
    }
