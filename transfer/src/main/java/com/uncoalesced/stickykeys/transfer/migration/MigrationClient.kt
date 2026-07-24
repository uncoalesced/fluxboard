// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.migration

import com.uncoalesced.stickykeys.transfer.crypto.CryptoManager
import com.uncoalesced.stickykeys.transfer.pairing.PairingManager
import com.uncoalesced.stickykeys.transfer.pairing.PairingToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.net.Socket
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

sealed interface MigrationClientState {
    data object Idle : MigrationClientState
    data object Connecting : MigrationClientState
    data class Transferring(val progress: Float) : MigrationClientState
    data object Extracting : MigrationClientState
    data object Success : MigrationClientState
    data class Error(val message: String) : MigrationClientState
}

@Singleton
class MigrationClient @Inject constructor(
    private val pairingManager: PairingManager,
    private val cryptoManager: CryptoManager,
    private val packager: MigrationPackager
) {
    private val _state = MutableStateFlow<MigrationClientState>(MigrationClientState.Idle)
    val state: StateFlow<MigrationClientState> = _state.asStateFlow()

    suspend fun startTransfer(tokenString: String) = withContext(Dispatchers.IO) {
        try {
            _state.value = MigrationClientState.Connecting
            
            // This verifies expiry and derives the shared secret implicitly if valid
            val verifyResult = pairingManager.verifyAndAcceptToken(tokenString)
            if (verifyResult.isFailure) {
                throw verifyResult.exceptionOrNull() ?: Exception("Invalid token")
            }

            val token = PairingToken.fromJsonString(tokenString)!!
            val myPublicKey = cryptoManager.getMyPublicKey() ?: throw IllegalStateException("Key pair missing")
            val secret = pairingManager.getSharedSecret() ?: throw IllegalStateException("Shared secret missing")
            
            val socket = Socket(token.ip, token.port)
            val dataOut = DataOutputStream(socket.getOutputStream())
            val dataIn = DataInputStream(socket.getInputStream())

            // 1. Send our public key
            dataOut.writeUTF(myPublicKey)
            dataOut.flush()

            // 2. Read Metadata
            val iv = ByteArray(12)
            dataIn.readFully(iv)
            val fileLength = dataIn.readLong()
            val expectedChecksum = ByteArray(32)
            dataIn.readFully(expectedChecksum)

            // 3. Setup GCM Cipher
            val secretKey = SecretKeySpec(secret, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

            // 4. Download and Decrypt
            val cipherIn = CipherInputStream(socket.getInputStream(), cipher)
            val tempZip = File.createTempFile("migration_receive", ".zip")
            
            tempZip.outputStream().use { fileOut ->
                val buffer = ByteArray(8192)
                var totalRead = 0L
                var bytesRead: Int
                
                while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
                    fileOut.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (fileLength > 0) {
                        _state.value = MigrationClientState.Transferring(totalRead.toFloat() / fileLength.toFloat())
                    }
                }
            }
            
            cipherIn.close()
            socket.close()

            // 5. Verify Checksum
            _state.value = MigrationClientState.Extracting
            val actualChecksum = computeChecksum(tempZip)
            if (!actualChecksum.contentEquals(expectedChecksum)) {
                tempZip.delete()
                throw SecurityException("Checksum verification failed! Data may be corrupted or tampered with.")
            }

            // 6. Extract
            tempZip.inputStream().use { fileIn ->
                packager.extractDataFromStream(fileIn)
            }
            tempZip.delete()

            _state.value = MigrationClientState.Success
        } catch (e: Exception) {
            _state.value = MigrationClientState.Error(e.message ?: "Transfer Error")
        }
    }
    
    fun reset() {
        _state.value = MigrationClientState.Idle
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
