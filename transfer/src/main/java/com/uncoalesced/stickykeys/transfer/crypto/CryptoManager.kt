// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.crypto

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.inject.Inject

/**
 * Handles Ephemeral Elliptic Curve Diffie-Hellman (ECDH) key exchange.
 */
class CryptoManager @Inject constructor() {

    private var keyPair: KeyPair? = null

    /**
     * Generates an ephemeral ECDH key pair (secp256r1) and returns the public key as a Base64 string.
     */
    fun generateEphemeralKeyPair(): String {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        val kp = keyPairGenerator.generateKeyPair()
        keyPair = kp
        
        return Base64.encodeToString(kp.public.encoded, Base64.NO_WRAP)
    }

    /**
     * Derives a shared secret (256-bit AES-compatible key) given the peer's Base64 public key.
     */
    fun computeSharedSecret(peerPublicKeyBase64: String): ByteArray {
        val kp = keyPair ?: throw IllegalStateException("Ephemeral key pair not generated yet.")
        
        val peerKeyBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)
        val keyFactory = KeyFactory.getInstance("EC")
        val peerPublicKey = keyFactory.generatePublic(X509EncodedKeySpec(peerKeyBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(kp.private)
        keyAgreement.doPhase(peerPublicKey, true)

        val secret = keyAgreement.generateSecret()
        
        // Hash the secret with SHA-256 to ensure a uniform 256-bit key for AES
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(secret)
    }

    fun getMyPublicKey(): String? {
        return keyPair?.let { Base64.encodeToString(it.public.encoded, Base64.NO_WRAP) }
    }
}
