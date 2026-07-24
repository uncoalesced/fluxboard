// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.uncoalesced.stickykeys.keyboardcore.data.local.KeyboardPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HapticsManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: KeyboardPreferences
) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun performKeyPressHaptic() {
        if (!preferences.hapticsEnabled.value) return
        
        val intensity = preferences.hapticsIntensity.value
        vibrate(10L, intensity)
    }

    fun performStickerSendHaptic() {
        if (!preferences.hapticsEnabled.value) return
        
        // Distinct, slightly longer haptic for sending sticker
        val intensity = (preferences.hapticsIntensity.value * 0.7f).toInt().coerceAtLeast(1)
        vibrate(20L, intensity)
    }

    private fun vibrate(durationMillis: Long, amplitude: Int) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createOneShot(durationMillis, amplitude.coerceIn(1, 255))
            v.vibrate(effect)
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(durationMillis)
        }
    }
}
