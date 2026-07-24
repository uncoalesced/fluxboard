// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.pairing

import com.uncoalesced.stickykeys.transfer.crypto.CryptoManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairingManager @Inject constructor(
    private val cryptoManager: CryptoManager,
    private val networkDiscoveryManager: NetworkDiscoveryManager
) {
    companion object {
        // Pairing codes expire after 5 minutes
        private const val TOKEN_EXPIRY_MS = 5 * 60 * 1000L
    }

    private var sharedSecret: ByteArray? = null

    /**
     * Generates a new pairing token string to be displayed as a QR code.
     * @param port The bound ServerSocket port.
     */
    fun generatePairingToken(port: Int): String {
        val publicKey = cryptoManager.generateEphemeralKeyPair()
        val ip = networkDiscoveryManager.getLocalIpAddress() ?: "127.0.0.1"
        
        val token = PairingToken(
            publicKeyBase64 = publicKey,
            ip = ip,
            port = port,
            timestamp = System.currentTimeMillis()
        )
        return token.toJsonString()
    }

    /**
     * Parses and verifies a scanned QR token. If valid and not expired, derives the shared secret.
     */
    fun verifyAndAcceptToken(tokenString: String): Result<Unit> {
        val token = PairingToken.fromJsonString(tokenString)
            ?: return Result.failure(IllegalArgumentException("Invalid QR Code format"))

        val now = System.currentTimeMillis()
        if (now - token.timestamp > TOKEN_EXPIRY_MS) {
            return Result.failure(IllegalStateException("Pairing code has expired"))
        }

        // Derive shared secret
        return try {
            // We need our own ephemeral key to establish trust back if we didn't generate one.
            // But since this is a receiver scanning, they should generate their key now.
            if (cryptoManager.getMyPublicKey() == null) {
                cryptoManager.generateEphemeralKeyPair()
            }
            
            sharedSecret = cryptoManager.computeSharedSecret(token.publicKeyBase64)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getSharedSecret(): ByteArray? = sharedSecret
}
