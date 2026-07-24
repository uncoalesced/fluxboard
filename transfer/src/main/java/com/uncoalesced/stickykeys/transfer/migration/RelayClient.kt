// Engineered by uncoalesced
package com.uncoalesced.stickykeys.transfer.migration

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class RelayClient @Inject constructor() {
    private val client = OkHttpClient.Builder().build()
    
    // Stub URL - in production this would be wss://relay.stickykeys.app
    private val relayUrl = "ws://10.0.2.2:8080"

    /**
     * Connects to the relay, sends the join handshake, and returns (InputStream, OutputStream).
     */
    suspend fun connectAndJoin(sessionId: String): Pair<InputStream, OutputStream> = suspendCancellableCoroutine { cont ->
        val request = Request.Builder().url(relayUrl).build()
        
        val pipedIn = PipedInputStream(1024 * 1024) // 1MB buffer
        val pipedOut = PipedOutputStream(pipedIn)
        
        var isJoined = false
        var isResumed = false

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Send join handshake
                val handshake = JSONObject().apply {
                    put("action", "join")
                    put("session_id", sessionId)
                }.toString()
                webSocket.send(handshake)
                isJoined = true
                
                // Return the streams
                if (!isResumed) {
                    isResumed = true
                    val wsOut = WebSocketOutputStream(webSocket)
                    cont.resume(Pair(pipedIn, wsOut))
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                pipedOut.write(bytes.toByteArray())
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                pipedOut.close()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                pipedOut.close()
                if (!isResumed) {
                    isResumed = true
                    cont.resumeWithException(t)
                }
            }
        }

        val webSocket = client.newWebSocket(request, listener)
        
        cont.invokeOnCancellation {
            webSocket.close(1000, "Cancelled")
            pipedOut.close()
        }
    }
}

class WebSocketOutputStream(private val webSocket: WebSocket) : OutputStream() {
    override fun write(b: Int) {
        webSocket.send(byteArrayOf(b.toByte()).toByteString())
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        webSocket.send(b.sliceArray(off until off + len).toByteString())
    }
}
