// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Phase 38: single source of truth for automatic incognito.
 *
 * True only while the keyboard is actually shown on an editor that sets
 * IME_FLAG_NO_PERSONALIZED_LEARNING. Set by [StickyKeysIME] from the input
 * session lifecycle and cleared when that session ends or the keyboard hides,
 * so it describes the field the user is on right now.
 *
 * Everything that must react to incognito reads this one flow -- the Lock
 * indicator (via TypingViewModel), the prediction learning gate, and clipboard
 * capture -- so there is no second copy to keep in sync.
 */
@Singleton
class IncognitoState
    @Inject
    constructor() {
        private val _active = MutableStateFlow(false)
        val active: StateFlow<Boolean> = _active.asStateFlow()

        fun set(enabled: Boolean) {
            _active.value = enabled
        }
    }
