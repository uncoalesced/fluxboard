// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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
 * [Spring.DampingRatioMediumBouncy] on a six-percent scale change overshoots by well under one
 * percent, which is why it is safe on a key this small -- but it was chosen without a device in
 * hand, so it is the first thing to damp further if the board feels rubbery in the hand.
 */
internal val PressSpring =
    spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessHigh,
    )

/**
 * The spring an expanding pill uses, in the app's bottom dock.
 *
 * Lower stiffness and no bounce, unlike [PressSpring]. A press is a small, felt event and wants
 * to read as something that moved; a dock item growing to three times its width is a layout
 * change, and overshoot there reads as the bar wobbling rather than as physicality. Here rather
 * than beside the dock so the motion language stays one definition -- this file is the reason
 * the key grid and the suggestion strip cannot drift apart, and the dock is now in the same
 * arrangement.
 */
val ExpandSpring =
    spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

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
