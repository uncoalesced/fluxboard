// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.ime

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.ClipboardDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ClipboardIMEViewModel(
    private val clipboardDao: ClipboardDao
) : ViewModel() {
    
    val clipboardEntries = clipboardDao.getAll().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun deleteEntry(id: Long) {
        // ClipboardDao methods are blocking; keep them off the main thread.
        viewModelScope.launch(Dispatchers.IO) {
            clipboardDao.deleteById(id)
        }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) {
            clipboardDao.deleteAll()
        }
    }
}
