// Engineered by uncoalesced
package com.uncoalesced.stickykeys.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import com.uncoalesced.stickykeys.R
import com.uncoalesced.stickykeys.keyboardcore.theme.StickyKeysTheme
import com.uncoalesced.stickykeys.transfer.migration.MigrationClient
import com.uncoalesced.stickykeys.transfer.migration.MigrationClientState
import com.uncoalesced.stickykeys.transfer.migration.MigrationServer
import com.uncoalesced.stickykeys.transfer.migration.MigrationServerState
import com.uncoalesced.stickykeys.transfer.pairing.PairingManager
import com.uncoalesced.stickykeys.ui.components.brandCheckboxColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import qrcode.QRCode
import javax.inject.Inject

/** Which camera-permission explanation, if any, is on screen. */
private enum class CameraPermissionPrompt {
    /** Shown before the system prompt, so the ask is never unexplained. */
    Rationale,

    /** Shown after a refusal, with a route to Settings and a workaround. */
    Denied,
}

sealed interface DevicePairingUiState {
    data object Idle : DevicePairingUiState

    data class Generating(
        val bitmap: Bitmap,
        val includeClipboard: Boolean,
    ) : DevicePairingUiState
}

@HiltViewModel
class DevicePairingViewModel
    @Inject
    constructor(
        private val pairingManager: PairingManager,
        val migrationServer: MigrationServer,
        val migrationClient: MigrationClient,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow<DevicePairingUiState>(DevicePairingUiState.Idle)
        val uiState: StateFlow<DevicePairingUiState> = _uiState.asStateFlow()

        fun startSenderFlow(includeClipboard: Boolean) {
            viewModelScope.launch {
                migrationServer.startServer(includeClipboard)
            }
        }

        // Called when the server successfully bounds and creates a token
        fun showQrCode(
            token: String,
            includeClipboard: Boolean,
        ) {
            try {
                val bitmap =
                    QRCode
                        .ofSquares()
                        .build(token)
                        .render()
                        .nativeImage() as Bitmap
                _uiState.value = DevicePairingUiState.Generating(bitmap, includeClipboard)
            } catch (e: Exception) {
                // Error handled by server state usually
            }
        }

        fun handleScanResult(result: ScanIntentResult) {
            // contents is null when the user backs out of the scanner.
            val tokenStr = result.contents ?: return
            viewModelScope.launch {
                migrationClient.startTransfer(tokenStr)
            }
        }

        fun reset() {
            migrationServer.stopServer()
            migrationClient.reset()
            _uiState.value = DevicePairingUiState.Idle
        }
    }

/**
 * Moving stickers to another phone, drawn inline at the foot of Settings.
 *
 * This was a screen of its own, first a dock tab and then a pushed route with an app bar. It is
 * something done about once per phone, so it now lives where the rest of the one-off actions
 * are, and the graph's top level is just the three dock screens.
 *
 * Leaving Settings stops whatever transfer is in flight, which the old back arrow also did. The
 * reset is tied to this section leaving composition, not to the activity stopping: launching
 * the QR scanner stops the activity, and so does the screen switching off, and neither should
 * kill a transfer. A configuration change is skipped for the same reason.
 */
@Composable
fun TransferSection(
    modifier: Modifier = Modifier,
    viewModel: DevicePairingViewModel = hiltViewModel(),
) {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(viewModel) {
        onDispose {
            if (activity?.isChangingConfigurations != true) viewModel.reset()
        }
    }

    val state by viewModel.uiState.collectAsState()
    val serverState by viewModel.migrationServer.state.collectAsState()
    val clientState by viewModel.migrationClient.state.collectAsState()

    var includeClipboard by remember { mutableStateOf(false) }

    val scanQrLauncher =
        rememberLauncherForActivityResult(ScanContract()) { result ->
            viewModel.handleScanResult(result)
        }
    val scanOptions =
        remember {
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("Scan the pairing code shown on the other device")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        }

    // CAMERA gate. The scanner activity would otherwise raise the system prompt itself,
    // cold, with no statement of why a keyboard app wants the camera -- the single worst
    // moment to ask a privacy-minded user for a dangerous permission.
    val context = LocalContext.current
    var permissionPrompt by remember { mutableStateOf<CameraPermissionPrompt?>(null) }

    val requestCamera =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                scanQrLauncher.launch(scanOptions)
            } else {
                permissionPrompt = CameraPermissionPrompt.Denied
            }
        }

    fun beginScan() {
        val alreadyGranted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            scanQrLauncher.launch(scanOptions)
        } else {
            permissionPrompt = CameraPermissionPrompt.Rationale
        }
    }

    when (permissionPrompt) {
        CameraPermissionPrompt.Rationale ->
            AlertDialog(
                onDismissRequest = { permissionPrompt = null },
                title = { Text(stringResource(R.string.text_camera_permission_title)) },
                text = { Text(stringResource(R.string.text_camera_permission_rationale)) },
                confirmButton = {
                    Button(onClick = {
                        permissionPrompt = null
                        requestCamera.launch(Manifest.permission.CAMERA)
                    }) {
                        Text(stringResource(R.string.text_continue))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { permissionPrompt = null }) {
                        Text(stringResource(R.string.text_not_now))
                    }
                },
            )

        CameraPermissionPrompt.Denied ->
            AlertDialog(
                onDismissRequest = { permissionPrompt = null },
                title = { Text(stringResource(R.string.text_camera_permission_title)) },
                text = { Text(stringResource(R.string.text_camera_permission_denied)) },
                confirmButton = {
                    Button(onClick = {
                        permissionPrompt = null
                        context.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            ),
                        )
                    }) {
                        Text(stringResource(R.string.text_open_settings))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { permissionPrompt = null }) {
                        Text(stringResource(R.string.text_cancel))
                    }
                },
            )

        null -> Unit
    }

    // React to server state changing to WaitingForConnection
    LaunchedEffect(serverState) {
        if (serverState is MigrationServerState.WaitingForConnection) {
            val token =
                (serverState as MigrationServerState.WaitingForConnection)
                    .pairingToken
            viewModel.showQrCode(token, includeClipboard)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        when {
            clientState !is MigrationClientState.Idle -> {
                ClientProgressView(clientState, viewModel::reset)
            }
            serverState !is MigrationServerState.Idle -> {
                ServerProgressView(serverState, state, viewModel::reset)
            }
            else -> {
                Text(
                    text = stringResource(R.string.text_transfer_row_summary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = includeClipboard,
                        onCheckedChange = { includeClipboard = it },
                        colors = brandCheckboxColors(),
                    )
                    Text(
                        stringResource(R.string.text_include_clipboard_history_sensitive),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                // Side by side rather than stacked: two equal choices, and a 56dp pair of
                // full-width buttons was most of a screen for a section that is used once.
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { viewModel.startSenderFlow(includeClipboard) },
                        // The one primary action here, so the accent fill with a black label
                        // (5.18), as the brand kit draws an accent button.
                        colors =
                            ButtonDefaults.buttonColors(
                                containerColor = StickyKeysTheme.colors.primary,
                                contentColor = StickyKeysTheme.colors.onPrimary,
                            ),
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.text_transfer_send))
                    }
                    OutlinedButton(
                        onClick = { beginScan() },
                        modifier = Modifier.weight(1f).height(48.dp),
                    ) {
                        Text(stringResource(R.string.text_transfer_receive))
                    }
                }
            }
        }
    }
}

/** The hosting activity, unwrapping any ContextWrapper the theme or Hilt put around it. */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun ServerProgressView(
    serverState: MigrationServerState,
    uiState: DevicePairingUiState,
    onReset: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (serverState) {
            is MigrationServerState.WaitingForConnection -> {
                if (uiState is DevicePairingUiState.Generating) {
                    Text(
                        stringResource(R.string.text_scan_this_code_on_the_receiving_device),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (uiState.includeClipboard) {
                        Text(
                            stringResource(R.string.text_warning_sending_clipboard_history),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Image(
                        bitmap = uiState.bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.desc_pairing_qr_code),
                        // On a white tile with a margin. The bitmap's light modules are
                        // transparent, so on the card it drew dark-on-dark with no quiet
                        // zone, which is hard to read for a person and harder for a scanner.
                        // White is the one colour here that is not a palette choice: it is
                        // what QR readers expect behind the code.
                        modifier =
                            Modifier
                                .size(280.dp)
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(16.dp),
                    )
                }
                Spacer(modifier = Modifier.height(32.dp))
                TextButton(onClick = onReset) { Text(stringResource(R.string.text_cancel)) }
            }
            is MigrationServerState.PackagingData -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.text_packaging_local_data_securely))
            }
            is MigrationServerState.Transferring -> {
                LinearProgressIndicator(
                    progress = { serverState.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Transferring: ${(serverState.progress * 100).toInt()}%")
            }
            is MigrationServerState.Success -> {
                Text(
                    stringResource(R.string.text_transfer_complete),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onReset) { Text(stringResource(R.string.text_done)) }
            }
            is MigrationServerState.Error -> {
                Text(
                    "Server Error: ${serverState.message}",
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onReset) { Text(stringResource(R.string.text_back)) }
            }
            else -> {}
        }
    }
}

@Composable
fun ClientProgressView(
    clientState: MigrationClientState,
    onReset: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        when (clientState) {
            is MigrationClientState.Connecting -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.text_connecting_to_sender))
            }
            is MigrationClientState.Transferring -> {
                LinearProgressIndicator(
                    progress = { clientState.progress },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text("Receiving: ${(clientState.progress * 100).toInt()}%")
            }
            is MigrationClientState.Extracting -> {
                CircularProgressIndicator()
                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.text_verifying_checksum_extracting))
            }
            is MigrationClientState.Success -> {
                Text(
                    stringResource(R.string.text_migration_successful),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    stringResource(
                        R.string.text_please_restart_the_app_to_see_all_transferred_data,
                    ),
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onReset) { Text(stringResource(R.string.text_done)) }
            }
            is MigrationClientState.Error -> {
                Text(
                    "Client Error: ${clientState.message}",
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onReset) { Text(stringResource(R.string.text_back)) }
            }
            else -> {}
        }
    }
}
