// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.migration

import com.uncoalesced.stickykeys.transfer.crypto.CryptoManager
import com.uncoalesced.stickykeys.transfer.pairing.PairingManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Stopping a server that is waiting for the other phone must leave it Idle.
 *
 * Closing the socket is what wakes the blocked accept(), so an exception always follows a stop.
 * It used to land after stopServer() had set Idle and replace it with "Socket closed", which the
 * transfer section then showed the next time it was opened.
 */
class MigrationServerStopTest {
    @Test
    fun `stopping while waiting for a connection leaves the server idle`() =
        runBlocking {
            val pairing = mock(PairingManager::class.java)
            `when`(pairing.generatePairingToken(anyInt())).thenReturn("token")
            val server =
                MigrationServer(
                    pairing,
                    mock(CryptoManager::class.java),
                    mock(MigrationPackager::class.java),
                )

            val running = launch(Dispatchers.IO) { server.startServer(includeClipboard = false) }
            withTimeout(5_000) {
                server.state.first { it is MigrationServerState.WaitingForConnection }
            }

            server.stopServer()
            running.join()

            assertEquals(MigrationServerState.Idle, server.state.value)
        }
}
