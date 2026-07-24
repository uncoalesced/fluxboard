// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.migration

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

sealed interface TransferState {
    data object Idle : TransferState
    data class LinkReady(val linkUrl: String) : TransferState
    data class Transferring(val progress: Float) : TransferState
    data object Success : TransferState
    data class Error(val message: String) : TransferState
}

@Singleton
class StickerTransferService @Inject constructor(
    private val linkSharingManager: LinkSharingManager,
    private val relayClient: RelayClient
) {
    private val _state = MutableStateFlow<TransferState>(TransferState.Idle)
    val state: StateFlow<TransferState> = _state.asStateFlow()

    private var currentJob: Job? = null

    /**
     * Sender side: Generates link, waits for connection on LAN or Relay, sends file, closes.
     */
    fun sendSticker(stickerFile: File) {
        currentJob?.cancel()
        currentJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val context = linkSharingManager.prepareShareLink()
                _state.value = TransferState.LinkReady(context.url)

                // Race LAN vs Relay
                val streams = raceConnections(context)
                
                _state.value = TransferState.Transferring(0f)

                val secretKey = SecretKeySpec(context.aesKey, "AES")
                val iv = ByteArray(12)
                SecureRandom().nextBytes(iv)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

                // Send Metadata
                streams.second.write(iv)
                streams.second.write(1) // version
                // we can't easily write Long via OutputStream, use a DataOutputStream or assume we don't need it if we stream to EOF

                val cipherOut = CipherOutputStream(streams.second, cipher)
                stickerFile.inputStream().use { fileIn ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    val fileLength = stickerFile.length().toFloat()
                    var bytesRead: Int
                    
                    while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                        cipherOut.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (fileLength > 0) {
                            _state.value = TransferState.Transferring(totalRead / fileLength)
                        }
                    }
                }
                cipherOut.close()
                streams.first.close()
                streams.second.close()

                _state.value = TransferState.Success
            } catch (e: CancellationException) {
                // Cancelled
            } catch (e: Exception) {
                _state.value = TransferState.Error(e.message ?: "Transfer Error")
            }
        }
    }

    private suspend fun raceConnections(context: ShareContext): Pair<InputStream, OutputStream> = coroutineScope {
        val deferred = CompletableDeferred<Pair<InputStream, OutputStream>>()

        // LAN Listener
        launch {
            try {
                val socket = context.lanServerSocket.accept()
                deferred.complete(Pair(socket.getInputStream(), socket.getOutputStream()))
            } catch (e: Exception) {
                // Ignore socket closed
            }
        }

        // Relay Listener
        launch {
            try {
                val streams = relayClient.connectAndJoin(context.sessionId)
                deferred.complete(streams)
            } catch (e: Exception) {
                // Ignore relay failures
            }
        }

        val result = deferred.await()
        context.lanServerSocket.close()
        result
    }

    /**
     * Receiver side: connects to link, decrypts, writes to temp file.
     */
    suspend fun receiveSticker(linkUrl: String, outputFile: File) = withContext(Dispatchers.IO) {
        val context = linkSharingManager.connectToLink(linkUrl)
        
        val iv = ByteArray(12)
        var readCount = 0
        while (readCount < 12) {
            val count = context.inputStream.read(iv, readCount, 12 - readCount)
            if (count == -1) throw Exception("EOF reading IV")
            readCount += count
        }
        val version = context.inputStream.read() // Skip version byte

        val secretKey = SecretKeySpec(context.aesKey, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

        val cipherIn = CipherInputStream(context.inputStream, cipher)
        
        outputFile.outputStream().use { fileOut ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (cipherIn.read(buffer).also { bytesRead = it } != -1) {
                fileOut.write(buffer, 0, bytesRead)
            }
        }
        cipherIn.close()
        context.outputStream.close()
        context.inputStream.close()
    }
    
    fun reset() {
        currentJob?.cancel()
        _state.value = TransferState.Idle
    }
}
