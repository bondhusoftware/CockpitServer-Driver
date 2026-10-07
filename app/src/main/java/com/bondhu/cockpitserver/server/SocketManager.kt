package com.bondhu.cockpitserver.server

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject

/**
 * Server Driver (v1): Socket.IO job channel।
 * SimSupport pattern: :6001-এ connect → subscribe{channel:"device-<id>"} →
 * `recharge.assigned` ইভেন্টে জব।
 */
class SocketManager(private val context: Context) {

    interface Listener {
        fun onConnected()
        fun onDisconnected()
        fun onJobAssigned(job: JSONObject)
    }

    var listener: Listener? = null
    private var socket: Socket? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile var connected: Boolean = false
        private set

    fun connect() {
        disconnect()
        try {
            val deviceId = ServerConfig.getDeviceId(context)
            if (deviceId.isBlank()) {
                Log.w(TAG, "No device_id, skip socket connect")
                return
            }
            val opts = IO.Options().apply {
                reconnection = true
                reconnectionAttempts = Int.MAX_VALUE
                reconnectionDelay = 3000
                timeout = 15000
                transports = arrayOf("websocket")
            }
            val s = IO.socket(ServerConfig.getSocketUrl(context), opts)
            socket = s

            s.on(Socket.EVENT_CONNECT) {
                connected = true
                Log.i(TAG, "Socket connected")
                // SimSupport-এর মতো subscribe
                try {
                    val sub = JSONObject().apply {
                        put("channel", "device-$deviceId")
                    }
                    s.emit("subscribe", sub)
                } catch (_: Exception) { }
                // server-কে জানাও: device online
                try {
                    ApiClient.get(context).connect(deviceId).execute()
                } catch (_: Exception) { }
                // register (device_type=cockpit) — upsert
                try {
                    ApiClient.get(context).register(
                        deviceId, ServerConfig.getDeviceName(), "cockpit"
                    ).execute()
                } catch (_: Exception) { }
                mainHandler.post { listener?.onConnected() }
            }

            s.on(Socket.EVENT_DISCONNECT) {
                connected = false
                Log.i(TAG, "Socket disconnected")
                mainHandler.post { listener?.onDisconnected() }
            }

            s.on(Socket.EVENT_CONNECT_ERROR) {
                connected = false
                mainHandler.post { listener?.onDisconnected() }
            }

            s.on("recharge.assigned") { args ->
                try {
                    val payload = args.firstOrNull() as? JSONObject ?: return@on
                    // SimSupport payload: {recharge: {...}} বা সরাসরি {...}
                    val job = if (payload.has("recharge")) payload.getJSONObject("recharge") else payload
                    Log.i(TAG, "Job assigned: ${job.optString("id")}")
                    mainHandler.post { listener?.onJobAssigned(job) }
                } catch (e: Exception) {
                    Log.w(TAG, "Bad job payload: ${e.message}")
                }
            }

            s.connect()
        } catch (e: Exception) {
            Log.w(TAG, "Socket connect failed: ${e.message}")
        }
    }

    fun disconnect() {
        try {
            socket?.off()
            socket?.disconnect()
            socket?.close()
        } catch (_: Exception) { }
        socket = null
        connected = false
    }

    companion object {
        private const val TAG = "CockpitSocket"
    }
}
