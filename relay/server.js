const { WebSocketServer } = require('ws');

const wss = new WebSocketServer({ port: process.env.PORT || 8080 });
console.log(`Relay server started on port ${wss.options.port}`);

// session_id -> { clients: Set<WebSocket>, timeout: NodeJS.Timeout }
const sessions = new Map();

const SESSION_TTL_MS = 10 * 60 * 1000; // 10 minutes

wss.on('connection', (ws) => {
    let currentSessionId = null;

    ws.on('message', (message, isBinary) => {
        if (!currentSessionId) {
            // First message must be the join handshake
            try {
                const data = JSON.parse(message.toString());
                if (data.action === 'join' && typeof data.session_id === 'string') {
                    currentSessionId = data.session_id;
                    
                    let session = sessions.get(currentSessionId);
                    if (!session) {
                        session = {
                            clients: new Set(),
                            timeout: setTimeout(() => {
                                closeSession(currentSessionId);
                            }, SESSION_TTL_MS)
                        };
                        sessions.set(currentSessionId, session);
                    }

                    if (session.clients.size >= 2) {
                        ws.close(1008, "Session full");
                        return;
                    }

                    session.clients.add(ws);
                    console.log(`Client joined session ${currentSessionId}. Total: ${session.clients.size}`);
                } else {
                    ws.close(1008, "Invalid handshake format");
                }
            } catch (e) {
                ws.close(1008, "Expected JSON handshake");
            }
        } else {
            // Already joined, blindly relay the message to the OTHER client in the session
            const session = sessions.get(currentSessionId);
            if (session) {
                for (const client of session.clients) {
                    if (client !== ws && client.readyState === 1 /* OPEN */) {
                        client.send(message, { binary: isBinary });
                    }
                }
            }
        }
    });

    ws.on('close', () => {
        if (currentSessionId) {
            closeSession(currentSessionId);
        }
    });
    
    ws.on('error', console.error);
});

function closeSession(sessionId) {
    const session = sessions.get(sessionId);
    if (session) {
        clearTimeout(session.timeout);
        for (const client of session.clients) {
            if (client.readyState === 1) {
                client.close(1000, "Session ended");
            }
        }
        sessions.delete(sessionId);
        console.log(`Session ${sessionId} closed and removed.`);
    }
}
