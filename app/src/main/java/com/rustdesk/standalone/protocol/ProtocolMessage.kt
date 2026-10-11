package com.rustdesk.standalone.protocol

import android.content.Context
import android.net.wifi.WifiManager
import com.google.gson.Gson
import java.net.Inet4Address
import java.net.NetworkInterface

data class HandshakeRequest(
    val pin: String,
    val clientName: String = "Client"
)

data class HandshakeResponse(
    val success: Boolean,
    val message: String = "",
    val screenWidth: Int = 0,
    val screenHeight: Int = 0
)

data class RemoteInputEvent(
    val action: String, // "DOWN", "MOVE", "UP"
    val normalizedX: Float,
    val normalizedY: Float
)

object ProtocolSerializer {
    private val gson = Gson()

    fun toJson(obj: Any): String = gson.toJson(obj)
    fun <T> fromJson(json: String, clazz: Class<T>): T = gson.fromJson(json, clazz)
}

object NetworkUtils {
    fun getLocalIpAddress(context: Context): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "127.0.0.1"
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "127.0.0.1"
    }
}
