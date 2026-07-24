// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.migration

import com.uncoalesced.stickykeys.transfer.crypto.CryptoManager
import com.uncoalesced.stickykeys.transfer.pairing.NetworkDiscoveryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import java.security.SecureRandom
import java.io.InputStream
import java.io.OutputStream

@Singleton
@OptIn(ExperimentalEncodingApi::class)
class LinkSharingManager @Inject constructor(
    private val networkDiscoveryManager: NetworkDiscoveryManager,
    private val relayClient: RelayClient
) {
    /**
     * Generates a sharing link and opens a ServerSocket for potential LAN-first connections.
     * Returns a Triple of (The Link URL, The AES Secret, The bound ServerSocket).
     */
    suspend fun prepareShareLink(): ShareContext = withContext(Dispatchers.IO) {
        val sessionId = UUID.randomUUID().toString()
        val aesKey = ByteArray(32)
        SecureRandom().nextBytes(aesKey)
        val base64Key = Base64.UrlSafe.encode(aesKey)

        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort
        val ip = networkDiscoveryManager.getLocalIpAddress() ?: "0.0.0.0"

        val link = "https://stickykeys.app/s#${sessionId}_${base64Key}_${ip}_${port}"
        
        ShareContext(link, sessionId, aesKey, serverSocket)
    }

    /**
     * Receiver side: Parse link and return connection streams (LAN or Relay).
     */
    suspend fun connectToLink(linkUrl: String): ReceiveContext = withContext(Dispatchers.IO) {
        val fragment = linkUrl.substringAfterLast("#", "")
        if (fragment.isEmpty()) throw IllegalArgumentException("Invalid link format")

        val parts = fragment.split("_")
        if (parts.size < 2) throw IllegalArgumentException("Invalid link format")

        val sessionId = parts[0]
        val aesKey = Base64.UrlSafe.decode(parts[1])
        
        var connectedIn: InputStream? = null
        var connectedOut: OutputStream? = null

        // Try LAN First if IP/Port are present
        if (parts.size == 4) {
            val ip = parts[2]
            val port = parts[3].toIntOrNull() ?: 0
            if (ip != "0.0.0.0" && port > 0) {
                try {
                    val socket = Socket()
                    // 1.5 second timeout for LAN
                    socket.connect(InetSocketAddress(ip, port), 1500)
                    connectedIn = socket.getInputStream()
                    connectedOut = socket.getOutputStream()
                } catch (e: Exception) {
                    // Fall back to relay
                }
            }
        }

        // Fallback to Relay
        if (connectedIn == null || connectedOut == null) {
            val streams = relayClient.connectAndJoin(sessionId)
            connectedIn = streams.first
            connectedOut = streams.second
        }

        ReceiveContext(connectedIn, connectedOut, aesKey)
    }
}

data class ShareContext(
    val url: String,
    val sessionId: String,
    val aesKey: ByteArray,
    val lanServerSocket: ServerSocket
)

data class ReceiveContext(
    val inputStream: InputStream,
    val outputStream: OutputStream,
    val aesKey: ByteArray
)
