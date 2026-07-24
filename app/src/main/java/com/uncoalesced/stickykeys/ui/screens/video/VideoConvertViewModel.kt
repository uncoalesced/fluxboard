// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens.video

import androidx.lifecycle.ViewModel
import com.uncoalesced.stickykeys.stickercore.domain.repository.StickerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

import com.uncoalesced.stickykeys.stickercore.animation.AnimatedStickerConverter

@HiltViewModel
class VideoConvertViewModel @Inject constructor(
    val repository: StickerRepository,
    val converter: AnimatedStickerConverter,
    val appPreferences: com.uncoalesced.stickykeys.data.local.AppPreferences
) : ViewModel()
