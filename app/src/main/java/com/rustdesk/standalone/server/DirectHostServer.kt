package com.rustdesk.standalone.server

import com.rustdesk.standalone.protocol.HandshakeRequest
import com.rustdesk.standalone.protocol.HandshakeResponse
import com.rustdesk.standalone.protocol.ProtocolSerializer
import com.rustdesk.standalone.protocol.RemoteInputEvent
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList

class DirectHostServer(
    private val port: Int,
    private val expectedPin: String,
    private val screenWidth: Int,
    private val screenHeight: Int
) {
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val connectedClients = CopyOnWriteArrayList<Socket>()

    fun start() {
        serverScope.launch {
            try {
                serverSocket = ServerSocket(port)
                while (isActive) {
                    val client = serverSocket?.accept() ?: break
                    launch { handleClient(client) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val dos = DataOutputStream(socket.getOutputStream())

            val line = reader.readLine() ?: return
            val request = ProtocolSerializer.fromJson(line, HandshakeRequest::class.java)

            if (request.pin == expectedPin) {
                val response = HandshakeResponse(
                    success = true,
                    message = "Connected successfully",
                    screenWidth = screenWidth,
                    screenHeight = screenHeight
                )
                dos.writeBytes(ProtocolSerializer.toJson(response) + "\n")
                dos.flush()

                connectedClients.add(socket)

                // Read continuous input events
                while (socket.isConnected && !socket.isClosed) {
                    val inputLine = reader.readLine() ?: break
                    try {
                        val input = ProtocolSerializer.fromJson(inputLine, RemoteInputEvent::class.java)
                        println("Received input: ${input.action} at (${input.normalizedX}, ${input.normalizedY})")
                    } catch (e: Exception) {
                        // ignore malformed input
                    }
                }
            } else {
                val response = HandshakeResponse(success = false, message = "Invalid PIN")
                dos.writeBytes(ProtocolSerializer.toJson(response) + "\n")
                dos.flush()
                socket.close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            connectedClients.remove(socket)
            try { socket.close() } catch (ignored: Exception) {}
        }
    }

    fun broadcastFrame(frameBytes: ByteArray) {
        if (connectedClients.isEmpty()) return

        for (client in connectedClients) {
            if (!client.isClosed) {
                try {
                    val dos = DataOutputStream(client.getOutputStream())
                    dos.writeInt(frameBytes.size)
                    dos.write(frameBytes)
                    dos.flush()
                } catch (e: Exception) {
                    connectedClients.remove(client)
                    try { client.close() } catch (ignored: Exception) {}
                }
            }
        }
    }

    fun stop() {
        serverScope.cancel()
        try { serverSocket?.close() } catch (ignored: Exception) {}
        for (client in connectedClients) {
            try { client.close() } catch (ignored: Exception) {}
        }
        connectedClients.clear()
    }
}
