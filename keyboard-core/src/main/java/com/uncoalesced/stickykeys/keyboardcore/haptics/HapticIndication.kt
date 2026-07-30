// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.haptics

import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import kotlinx.coroutines.launch

/**
 * Wraps the app's normal press indication so every press also produces a haptic.
 *
 * Chosen over editing call sites. The app has roughly ninety `onClick` handlers across twenty
 * files, and "every button vibrates" enforced by touching each one is a rule that holds only
 * until the next button is added -- the first screen written after this change would silently
 * not have it.
 *
 * [LocalIndication] is the one place they genuinely share: `Modifier.clickable` defaults its
 * indication to it, and every Material button, icon button and clickable card is built on
 * `clickable`. Providing a wrapper here reaches all of them at once, including ones that do
 * not exist yet, and keeps the ripple by delegating to whatever indication was already in
 * scope rather than replacing it.
 *
 * Presses are read from the [InteractionSource], so this fires on press-down -- when the
 * finger lands, which is when a keyboard is expected to answer -- and not on click-release.
 * It also means a press that is dragged away and cancelled still gives the feedback that the
 * touch registered, which is the truth.
 */
private class HapticIndication(
    private val base: IndicationNodeFactory,
    private val haptics: HapticsManager,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        HapticIndicationNode(base, interactionSource, haptics)

    override fun equals(other: Any?): Boolean =
        other is HapticIndication && other.base == base && other.haptics === haptics

    override fun hashCode(): Int = 31 * base.hashCode() + haptics.hashCode()
}

private class HapticIndicationNode(
    base: IndicationNodeFactory,
    private val interactionSource: InteractionSource,
    private val haptics: HapticsManager,
) : DelegatingNode() {
    init {
        // Delegation rather than reimplementation: the ripple is somebody else's job and
        // this node has no business drawing one.
        val inner = base.create(interactionSource)
        if (inner is Modifier.Node) {
            @Suppress("UNCHECKED_CAST")
            delegate(inner as Modifier.Node)
        }
    }

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                if (interaction is PressInteraction.Press) {
                    haptics.performKeyPressHaptic()
                }
            }
        }
    }
}

/**
 * Installs app-wide press haptics. Wrap the content of every Compose entry point.
 *
 * Honours the same enable switch and strength slider as the keyboard, because it is the same
 * [HapticsManager] -- there is deliberately no second setting for "app buttons".
 */
@Composable
fun ProvideHapticIndication(
    haptics: HapticsManager,
    content: @Composable () -> Unit,
) {
    val base = ripple()
    val indication: Indication =
        remember(base, haptics) {
            HapticIndication(base as IndicationNodeFactory, haptics)
        }
    CompositionLocalProvider(LocalIndication provides indication, content = content)
}
