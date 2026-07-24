// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.migration

import com.uncoalesced.stickykeys.transfer.crypto.CryptoManager
import com.uncoalesced.stickykeys.transfer.pairing.PairingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.ServerSocket
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MigrationServerState {
    data object Idle : MigrationServerState
    data class WaitingForConnection(val pairingToken: String) : MigrationServerState
    data object PackagingData : MigrationServerState
    data class Transferring(val progress: Float) : MigrationServerState
    data object Success : MigrationServerState
    data class Error(val message: String) : MigrationServerState
}

@Singleton
class MigrationServer @Inject constructor(
    private val pairingManager: PairingManager,
    private val cryptoManager: CryptoManager,
    private val packager: MigrationPackager
) {
    private val _state = MutableStateFlow<MigrationServerState>(MigrationServerState.Idle)
    val state: StateFlow<MigrationServerState> = _state.asStateFlow()

    private var serverSocket: ServerSocket? = null

    suspend fun startServer(includeClipboard: Boolean) = withContext(Dispatchers.IO) {
        try {
            serverSocket = ServerSocket(0)
            val port = serverSocket!!.localPort
            val token = pairingManager.generatePairingToken(port)
            _state.value = MigrationServerState.WaitingForConnection(token)

            val socket = serverSocket!!.accept()
            _state.value = MigrationServerState.PackagingData

            val dataIn = DataInputStream(socket.getInputStream())
            val dataOut = DataOutputStream(socket.getOutputStream())

            // 1. Read Receiver's Public Key
            val receiverPubKey = dataIn.readUTF()
            
            // 2. Derive Shared Secret
            val secret = cryptoManager.computeSharedSecret(receiverPubKey)
            val secretKey = SecretKeySpec(secret, "AES")

            // 3. Package Data to Temp File
            val zipFile = packager.packageDataToTempFile(includeClipboard)
            
            // 4. Compute SHA-256 Checksum
            val checksum = computeChecksum(zipFile)

            // 5. Setup GCM Cipher
            val iv = ByteArray(12)
            SecureRandom().nextBytes(iv)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

            // 6. Send Metadata
            dataOut.write(iv)
            dataOut.writeLong(zipFile.length())
            dataOut.write(checksum)
            dataOut.flush()

            // 7. Send Encrypted Data
            val cipherOut = CipherOutputStream(socket.getOutputStream(), cipher)
            zipFile.inputStream().use { fileIn ->
                val buffer = ByteArray(8192)
                var totalRead = 0L
                val fileLength = zipFile.length().toFloat()
                var bytesRead: Int
                
                while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                    cipherOut.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (fileLength > 0) {
                        _state.value = MigrationServerState.Transferring(totalRead / fileLength)
                    }
                }
            }
            cipherOut.close() // GCM flushes auth tag here
            socket.close()
            serverSocket?.close()
            zipFile.delete()

            _state.value = MigrationServerState.Success
        } catch (e: Exception) {
            _state.value = MigrationServerState.Error(e.message ?: "Server Error")
            serverSocket?.close()
        }
    }
    
    fun stopServer() {
        try {
            serverSocket?.close()
        } catch (e: Exception) {}
        _state.value = MigrationServerState.Idle
    }

    private fun computeChecksum(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }
}
