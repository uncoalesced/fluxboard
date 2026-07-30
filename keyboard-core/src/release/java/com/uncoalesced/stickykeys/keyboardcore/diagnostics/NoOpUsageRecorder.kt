// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.diagnostics

import android.net.Uri
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The release build's usage recorder: nothing at all.
 *
 * This file is compiled only into release variants. Every method is empty, there is no file,
 * no counter and no state -- and, crucially, the class that *does* write a file is not in
 * this variant's source set, so it cannot be reached even reflectively.
 *
 * This is what makes "the diagnostics do not exist in a stable or F-Droid build" a true
 * statement about the artifact rather than a claim about a boolean.
 */
object NoOpUsageRecorder : UsageRecorder {
    override val enabled: Boolean = false

    override fun onSessionStart() = Unit

    override fun onSessionEnd() = Unit

    override fun onKeystroke() = Unit

    override fun onBackspace() = Unit

    override fun onAutocorrectAccepted() = Unit

    override fun onAutocorrectUndone() = Unit

    override fun onModeSwitch() = Unit

    override fun onScrubGesture() = Unit

    override fun shareIntentFile(): Uri? = null

    override fun clear() = Unit
}

@Module
@InstallIn(SingletonComponent::class)
object UsageRecorderModule {
    @Provides
    @Singleton
    fun provideUsageRecorder(): UsageRecorder = NoOpUsageRecorder
}
