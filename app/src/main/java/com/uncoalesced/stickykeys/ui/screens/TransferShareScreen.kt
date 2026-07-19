package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import com.uncoalesced.stickykeys.ui.components.LoadingScreen

sealed interface TransferShareUiState {
    data object Loading : TransferShareUiState
    data object Success : TransferShareUiState
}

@HiltViewModel
class TransferShareViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow<TransferShareUiState>(TransferShareUiState.Success)
    val uiState: StateFlow<TransferShareUiState> = _uiState.asStateFlow()
}

@Composable
fun TransferShareScreen(
    viewModel: TransferShareViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    
    when (state) {
        is TransferShareUiState.Loading -> LoadingScreen()
        is TransferShareUiState.Success -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Transfer & Share")
            }
        }
    }
}
