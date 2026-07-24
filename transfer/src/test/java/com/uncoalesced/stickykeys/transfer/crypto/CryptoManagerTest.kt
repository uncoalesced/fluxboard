// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.crypto

import android.os.Build
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class CryptoManagerTest {

    @Test
    fun `ephemeral key pair generates a valid base64 public key`() {
        val cryptoManager = CryptoManager()
        val publicKey = cryptoManager.generateEphemeralKeyPair()

        assertNotNull("Public key should not be null", publicKey)
        assert(publicKey.isNotEmpty()) { "Public key should not be empty" }
        // Verify it's valid base64 by decoding it (Base64.decode is tested by Robolectric)
        val decoded = android.util.Base64.decode(publicKey, android.util.Base64.NO_WRAP)
        assert(decoded.isNotEmpty()) { "Decoded bytes should not be empty" }
    }

    @Test
    fun `computeSharedSecret computes identical secrets for both parties`() {
        val alice = CryptoManager()
        val bob = CryptoManager()

        val alicePubKey = alice.generateEphemeralKeyPair()
        val bobPubKey = bob.generateEphemeralKeyPair()

        val aliceSecret = alice.computeSharedSecret(bobPubKey)
        val bobSecret = bob.computeSharedSecret(alicePubKey)

        assertNotNull(aliceSecret)
        assertNotNull(bobSecret)
        assert(aliceSecret.size == 32) { "Secret should be 256 bits (32 bytes)" }
        assertArrayEquals("Derived secrets should match", aliceSecret, bobSecret)
    }

    @Test
    fun `computeSharedSecret throws if key pair not generated`() {
        val cryptoManager = CryptoManager()
        val dummyPubKey = "dummy"

        assertThrows(IllegalStateException::class.java) {
            cryptoManager.computeSharedSecret(dummyPubKey)
        }
    }
}
