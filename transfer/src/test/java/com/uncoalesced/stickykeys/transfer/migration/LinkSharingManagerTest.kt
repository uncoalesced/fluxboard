// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.migration

import com.uncoalesced.stickykeys.transfer.pairing.NetworkDiscoveryManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
class LinkSharingManagerTest {

    private lateinit var networkDiscoveryManager: NetworkDiscoveryManager
    private lateinit var relayClient: RelayClient
    private lateinit var manager: LinkSharingManager

    @Before
    fun setup() {
        networkDiscoveryManager = mock(NetworkDiscoveryManager::class.java)
        relayClient = mock(RelayClient::class.java)
        manager = LinkSharingManager(networkDiscoveryManager, relayClient)
    }

    @Test
    fun `prepareShareLink generates valid link with LAN fallback`() {
        runBlocking {
            `when`(networkDiscoveryManager.getLocalIpAddress()).thenReturn("192.168.1.10")

            val context = manager.prepareShareLink()

            assertNotNull(context.url)
            assertNotNull(context.sessionId)
            assertNotNull(context.aesKey)
            assertNotNull(context.lanServerSocket)

            assertTrue(context.url.startsWith("https://stickykeys.app/s#"))
            val fragment = context.url.substringAfterLast("#")
            val parts = fragment.split("_")
            assertEquals(4, parts.size)
            assertEquals(context.sessionId, parts[0])
            assertEquals("192.168.1.10", parts[2])

            context.lanServerSocket.close()
        }
    }

    @Test
    fun `connectToLink parses correctly and falls back to relay if LAN fails`() {
        runBlocking {
            // Mock relay client to return dummy streams
            val dummyIn = ByteArrayInputStream(ByteArray(0))
            val dummyOut = ByteArrayOutputStream()
            `when`(relayClient.connectAndJoin("dummy-session")).thenReturn(Pair(dummyIn, dummyOut))

            // Create a link with a dead IP to force fallback
            val fakeKey = "testkey_12345678" // Needs to be some valid base64url length
            val encodedKey = Base64.UrlSafe.encode(fakeKey.toByteArray())
            val url = "https://stickykeys.app/s#dummy-session_${encodedKey}_192.0.2.0_9999"

            val receiveContext = manager.connectToLink(url)

            assertNotNull(receiveContext.inputStream)
            assertNotNull(receiveContext.outputStream)
            // Check that AES key was decoded correctly
            assertEquals(fakeKey, String(receiveContext.aesKey))
        }
    }

    @Test
    fun `connectToLink throws on invalid format`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                manager.connectToLink("https://stickykeys.app/s")
            }
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                manager.connectToLink("https://stickykeys.app/s#justonesection")
            }
        }
    }
}
