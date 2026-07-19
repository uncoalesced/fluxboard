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

sealed interface AppSettingsUiState {
    data object Loading : AppSettingsUiState
    data object Success : AppSettingsUiState
}

@HiltViewModel
class AppSettingsViewModel @Inject constructor() : ViewModel() {
    private val _uiState = MutableStateFlow<AppSettingsUiState>(AppSettingsUiState.Success)
    val uiState: StateFlow<AppSettingsUiState> = _uiState.asStateFlow()
}

@Composable
fun AppSettingsScreen(
    viewModel: AppSettingsViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    
    when (state) {
        is AppSettingsUiState.Loading -> LoadingScreen()
        is AppSettingsUiState.Success -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("App Settings")
            }
        }
    }
}
