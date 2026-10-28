// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import android.provider.Settings
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext

/*
 * The keyboard's motion language, in one place.
 *
 * A sixth token type alongside colours, typography, spacing, shapes and key style, and here
 * for the same reason those are: two surfaces that each define their own "0.94" are two
 * surfaces that drift apart the first time either is tuned. The key grid and the suggestion
 * strip are meant to feel like the same object responding to the same finger, and that is a
 * property of shared constants rather than of matching intentions.
 *
 * Nothing here is themeable. Colour and shape are taste; the timing of a press is closer to
 * ergonomics, and a preset that got it wrong would make the whole keyboard feel broken rather
 * than merely unattractive.
 */

/**
 * Key and chip colour transition length, in milliseconds.
 *
 * Short enough that ~80 concurrent transitions stay cheap when a mode change repaints the
 * whole board, and long enough that a release reads as a fade rather than a cut.
 */
const val PRESS_COLOR_ANIM_MS = 110

/** How far a pressed surface moves toward the accent. Visible under a fingertip, not garish. */
internal const val PRESS_BLEND = 0.3f

/**
 * Fade length for a panel that opens or closes, in milliseconds.
 *
 * Longer than [PRESS_COLOR_ANIM_MS] because it describes a different kind of event: a press is
 * a confirmation and wants to be instant, while a panel arriving is a change of context and
 * reads as jarring if it simply appears at full strength partway through its own slide.
 */
internal const val PANEL_FADE_MS = 180

/**
 * How far a pressed surface shrinks.
 *
 * Six percent: enough to read at the key's *edges*, which is the point -- a fingertip covers
 * the middle of a key, so the only feedback it cannot hide is one that happens around it. That
 * is also why this is not a ripple. A ripple was deliberately rejected for key presses because
 * it animates from under the finger; scale is a different mechanism rather than a reversal of
 * that decision.
 */
internal const val PRESS_SCALE = 0.94f

/**
 * What a press does to the resting elevation of a surface that has one.
 *
 * The shadow compresses rather than disappearing, so the key reads as pushed down into the
 * board instead of switching off. Multiplies the theme's own haze radius rather than replacing
 * it, so a preset that asks for no depth still gets none.
 */
internal const val PRESS_ELEVATION_SINK = 0.4f

/**
 * The press spring.
 *
 * A spring rather than the tween the colour uses, and the difference is deliberate. A colour
 * blend is a state change and wants to be over quickly; a press is a physical event, and a
 * spring is what makes it read as something that moved rather than something that faded.
 *
 * Held as one instance because it reaches every key: a spec allocated per recomposition would
 * be ~80 objects per repaint for a value that never varies.
 *
 * Critically damped. It used to be [Spring.DampingRatioMediumBouncy], which overshot by under one
 * percent on a key; the overshoot was invisible as movement and only made the board feel
 * slightly rubbery. Nothing in this keyboard bounces now: a key settles, it does not wobble.
 */
internal val PressSpring =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh,
    )

/**
 * The press animation for the current motion setting: [PressSpring], or an instant snap when
 * the system animator scale is zero. Reads a static composition local, so calling it from every
 * key costs a lookup and never a recomposition.
 */
@Composable
@ReadOnlyComposable
internal fun pressAnimationSpec(): AnimationSpec<Float> =
    if (isReducedMotion(LocalAnimatorScale.current)) snap() else PressSpring

/**
 * Decelerating curve shared by every transition here. Ease-out, because the thing that moved is
 * already arriving when the eye finds it, and no overshoot, because an overshoot on a layout
 * change reads as the surface wobbling.
 */
val MotionEaseOut: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)

/** Length of the dock pill's width change and label fade, in the app's bottom bar. */
const val DOCK_EXPAND_MS = 200

/** Fade length for moving between screens in the app. */
const val SCREEN_FADE_MS = 180

/** How long the quick-access chevron takes to turn over. */
internal const val CHEVRON_TURN_MS = 160

/**
 * Upper bound on every transition in this file. A transition longer than this stops reading as
 * feedback and starts reading as a wait; `MotionTokensTest` holds every constant under it.
 */
const val MAX_TRANSITION_MS = 200

/**
 * The system animator duration scale, as `Settings.Global.ANIMATOR_DURATION_SCALE` reports it.
 *
 * Static because it is provided once at a root (the IME view, the app's activity) and read by
 * every key. A static local is a lookup for its readers, where a dynamic one would subscribe
 * each of ~80 keys to a value that changes about never.
 */
val LocalAnimatorScale = staticCompositionLocalOf { 1f }

/** True when the user has switched animations off ("Remove animations", or the scale at 0). */
fun isReducedMotion(animatorScale: Float): Boolean = animatorScale <= 0f

/**
 * A transition's length under the given animator scale: unchanged normally, zero when the user
 * has turned animation off. Pure so the rule is testable without a device.
 */
fun motionDurationMs(
    baseMs: Int,
    animatorScale: Float,
): Int = if (isReducedMotion(animatorScale)) 0 else baseMs

/** [motionDurationMs] for the scale provided by [LocalAnimatorScale]. */
@Composable
@ReadOnlyComposable
fun motionMs(baseMs: Int): Int = motionDurationMs(baseMs, LocalAnimatorScale.current)

/**
 * Reads the system animator scale once per context. A missing or unreadable setting is the
 * platform default of 1, which is what an unset value means on a stock device.
 */
@Composable
fun rememberSystemAnimatorScale(): Float {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
    }
}

/**
 * Share of the dock a collapsed and an expanded item each take.
 *
 * Weights rather than fixed widths. The reference image does not resize the other items when
 * one expands, but a fixed-pixel expansion on a narrow phone either overflows or forces the
 * dock itself to grow, neither of which the reference shows either. Sharing the row keeps every
 * state a valid layout at every screen width with no special case.
 */
const val DOCK_COLLAPSED_WEIGHT = 1f
const val DOCK_EXPANDED_WEIGHT = 2.4f

/**
 * The fill a surface shows while pressed, given the fill it shows at rest.
 *
 * Blended rather than composited: the fill is usually opaque, so compositing the accent behind
 * it would change nothing. Alpha is carried over from the original so a deliberately
 * translucent surface stays translucent while pressed.
 */
internal fun pressedFill(
    resting: Color,
    accent: Color,
): Color = lerp(resting, accent, PRESS_BLEND).copy(alpha = resting.alpha)
