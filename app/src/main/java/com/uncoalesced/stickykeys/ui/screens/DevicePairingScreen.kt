// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import androidx.compose.ui.res.stringResource
import com.uncoalesced.stickykeys.R

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.uncoalesced.stickykeys.transfer.migration.MigrationClient
import com.uncoalesced.stickykeys.transfer.migration.MigrationServer
import com.uncoalesced.stickykeys.transfer.pairing.PairingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import qrcode.QRCode
import io.github.g00fy2.quickie.QRResult
import io.github.g00fy2.quickie.ScanQRCode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface DevicePairingUiState {
    data object Idle : DevicePairingUiState
    data class Generating(val bitmap: Bitmap, val includeClipboard: Boolean) : DevicePairingUiState
}

@HiltViewModel
class DevicePairingViewModel @Inject constructor(
    private val pairingManager: PairingManager,
    val migrationServer: MigrationServer,
    val migrationClient: MigrationClient
) : ViewModel() {
    private val _uiState = MutableStateFlow<DevicePairingUiState>(DevicePairingUiState.Idle)
    val uiState: StateFlow<DevicePairingUiState> = _uiState.asStateFlow()

    fun startSenderFlow(includeClipboard: Boolean) {
        viewModelScope.launch {
            migrationServer.startServer(includeClipboard)
        }
    }
    
    // Called when the server successfully bounds and creates a token
    fun showQrCode(token: String, includeClipboard: Boolean) {
        try {
            val bitmap = QRCode.ofSquares().build(token).render().nativeImage() as Bitmap
            _uiState.value = DevicePairingUiState.Generating(bitmap, includeClipboard)
        } catch (e: Exception) {
            // Error handled by server state usually
        }
    }

    fun handleScanResult(result: QRResult) {
        when (result) {
            is QRResult.QRSuccess -> {
                val tokenStr = result.content.rawValue ?: ""
                viewModelScope.launch {
                    migrationClient.startTransfer(tokenStr)
                }
            }
            is QRResult.QRError -> {
                // Should show error via state
            }
            else -> {}
        }
    }
    
    fun reset() {
        migrationServer.stopServer()
        migrationClient.reset()
        _uiState.value = DevicePairingUiState.Idle
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePairingScreen(
    viewModel: DevicePairingViewModel = hiltViewModel(),
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val serverState by viewModel.migrationServer.state.collectAsState()
    val clientState by viewModel.migrationClient.state.collectAsState()

    var includeClipboard by remember { mutableStateOf(false) }

    val scanQrLauncher = rememberLauncherForActivityResult(ScanQRCode()) { result ->
        viewModel.handleScanResult(result)
    }
    
    // React to server state changing to WaitingForConnection
    LaunchedEffect(serverState) {
        if (serverState is com.uncoalesced.stickykeys.transfer.migration.MigrationServerState.WaitingForConnection) {
            val token = (serverState as com.uncoalesced.stickykeys.transfer.migration.MigrationServerState.WaitingForConnection).pairingToken
            viewModel.showQrCode(token, includeClipboard)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.text_device_pairing)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.desc_back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Determine what to show based on client and server states
            when {
                clientState !is com.uncoalesced.stickykeys.transfer.migration.MigrationClientState.Idle -> {
                    ClientProgressView(clientState, viewModel::reset)
                }
                serverState !is com.uncoalesced.stickykeys.transfer.migration.MigrationServerState.Idle -> {
                    ServerProgressView(serverState, state, viewModel::reset)
                }
                else -> {
                    // Idle state
                    Text(
                        text = "Migrate your stickers to another device over your local network.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = includeClipboard,
                            onCheckedChange = { includeClipboard = it }
                        )
                        Text(stringResource(R.string.text_include_clipboard_history_sensitive))
                    }

                    Button(
                        onClick = { viewModel.startSenderFlow(includeClipboard) },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(stringResource(R.string.text_i_am_the_sender_show_qr))
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedButton(
                        onClick = { scanQrLauncher.launch(null) },
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Text(stringResource(R.string.text_i_am_the_receiver_scan_qr))
                    }
                }
            }
        }
    }
}

@Composable
fun ServerProgressView(
    serverState: com.uncoalesced.stickykeys.transfer.migration.MigrationServerState,
    uiState: DevicePairingUiState,
    onReset: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (serverState) {
            is com.uncoalesced.stickykeys.transfer.migration.MigrationServerState.WaitingForConnection -> {
                if (uiState is DevicePairingUiState.Generating) {
                    Text(stringResource(R.string.text_scan_this_code_on_the_receiving_device), style = MaterialTheme.typography.titleMedium)
                    if (uiState.includeClipboard) {
                        Text(stringResource(R.string.text_warning_sending_clipboard_history), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Image(
                        bitmap = uiState.bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.desc_pairing_qr_code),
                        modifier = Modifier.size(300.dp)
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                TextButton(onClick = onReset) { Text(stringResource(R.string.text_cancel)) }
            }
            is com.uncoalesced.stickykeys.transfer.migration.MigrationServerState.PackagingData -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.text_packaging_local_data_securely))
            }
            is com.uncoalesced.stickykeys.transfer.migration.MigrationServerState.Transferring -> {
                LinearProgressIndicator(progress = { serverState.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                Text("Transferring: ${(serverState.progress * 100).toInt()}%")
            }
            is com.uncoalesced.stickykeys.transfer.migration.MigrationServerState.Success -> {
                Text(stringResource(R.string.text_transfer_complete), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onReset) { Text(stringResource(R.string.text_done)) }
            }
            is com.uncoalesced.stickykeys.transfer.migration.MigrationServerState.Error -> {
                Text("Server Error: ${serverState.message}", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onReset) { Text(stringResource(R.string.text_back)) }
            }
            else -> {}
        }
    }
}

@Composable
fun ClientProgressView(
    clientState: com.uncoalesced.stickykeys.transfer.migration.MigrationClientState,
    onReset: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (clientState) {
            is com.uncoalesced.stickykeys.transfer.migration.MigrationClientState.Connecting -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.text_connecting_to_sender))
            }
            is com.uncoalesced.stickykeys.transfer.migration.MigrationClientState.Transferring -> {
                LinearProgressIndicator(progress = { clientState.progress }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(16.dp))
                Text("Receiving: ${(clientState.progress * 100).toInt()}%")
            }
            is com.uncoalesced.stickykeys.transfer.migration.MigrationClientState.Extracting -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.text_verifying_checksum_extracting))
            }
            is com.uncoalesced.stickykeys.transfer.migration.MigrationClientState.Success -> {
                Text(stringResource(R.string.text_migration_successful), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.text_please_restart_the_app_to_see_all_transferred_data))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onReset) { Text(stringResource(R.string.text_done)) }
            }
            is com.uncoalesced.stickykeys.transfer.migration.MigrationClientState.Error -> {
                Text("Client Error: ${clientState.message}", color = MaterialTheme.colorScheme.error)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onReset) { Text(stringResource(R.string.text_back)) }
            }
            else -> {}
        }
    }
}
