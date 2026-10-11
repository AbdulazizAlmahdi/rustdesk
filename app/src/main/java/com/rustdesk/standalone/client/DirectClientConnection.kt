package com.rustdesk.standalone.client

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.rustdesk.standalone.protocol.HandshakeRequest
import com.rustdesk.standalone.protocol.HandshakeResponse
import com.rustdesk.standalone.protocol.ProtocolSerializer
import com.rustdesk.standalone.protocol.RemoteInputEvent
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStreamReader
import java.net.Socket

class DirectClientConnection(
    private val host: String,
    private val port: Int,
    private val pin: String,
    private val onConnected: (screenWidth: Int, screenHeight: Int) -> Unit,
    private val onFrameReceived: (Bitmap) -> Unit,
    private val onError: (String) -> Unit
) {
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var socket: Socket? = null
    private var dos: DataOutputStream? = null

    fun connect() {
        clientScope.launch {
            try {
                val s = Socket(host, port)
                socket = s
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                dos = DataOutputStream(s.getOutputStream())
                val dis = DataInputStream(s.getInputStream())

                // Send Handshake
                val request = HandshakeRequest(pin = pin, clientName = "Android-Viewer")
                dos?.writeBytes(ProtocolSerializer.toJson(request) + "\n")
                dos?.flush()

                // Read Handshake response
                val respLine = reader.readLine() ?: throw Exception("Empty response from host")
                val response = ProtocolSerializer.fromJson(respLine, HandshakeResponse::class.java)

                if (!response.success) {
                    withContext(Dispatchers.Main) {
                        onError(response.message.ifEmpty { "Authentication failed" })
                    }
                    disconnect()
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    onConnected(response.screenWidth, response.screenHeight)
                }

                // Loop reading video frames
                while (isActive && s.isConnected && !s.isClosed) {
                    val frameLength = dis.readInt()
                    if (frameLength <= 0 || frameLength > 10 * 1024 * 1024) continue

                    val buffer = ByteArray(frameLength)
                    dis.readFully(buffer)

                    val bitmap = BitmapFactory.decodeByteArray(buffer, 0, buffer.size)
                    if (bitmap != null) {
                        withContext(Dispatchers.Main) {
                            onFrameReceived(bitmap)
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Connection error")
                }
                disconnect()
            }
        }
    }

    fun sendInput(action: String, normalizedX: Float, normalizedY: Float) {
        clientScope.launch {
            try {
                val event = RemoteInputEvent(action, normalizedX, normalizedY)
                dos?.writeBytes(ProtocolSerializer.toJson(event) + "\n")
                dos?.flush()
            } catch (e: Exception) {
                // ignore transient write errors
            }
        }
    }

    fun disconnect() {
        clientScope.cancel()
        try { socket?.close() } catch (ignored: Exception) {}
        socket = null
        dos = null
    }
}
