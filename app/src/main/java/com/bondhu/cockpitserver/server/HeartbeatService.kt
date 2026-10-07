package com.bondhu.cockpitserver.server

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import org.json.JSONObject

/**
 * Server Driver (v1): Foreground service — socket alive + 60s heartbeat।
 * SimSupport-এর HeartbeatForegroundService pattern।
 */
class HeartbeatService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var socketManager: SocketManager
    private lateinit var jobManager: JobManager

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            sendHeartbeat()
            handler.postDelayed(this, 60_000)
        }
    }

    // v3: HTTP polling — Socket.IO-এর বদলে নির্ভরযোগ্য job fetch (প্রতি 10 সেকেন্ড)
    private val pollRunnable = object : Runnable {
        override fun run() {
            pollPendingJobs()
            handler.postDelayed(this, 10_000)
        }
    }

    private fun pollPendingJobs() {
        if (!ServerConfig.isEnabled(this)) return
        val deviceId = ServerConfig.getDeviceId(this)
        if (deviceId.isBlank()) return
        Thread {
            try {
                val resp = ApiClient.get(this).getPendingJobs(deviceId).execute()
                if (resp.isSuccessful) {
                    ConnectionState.connected = true // polling success = server reachable
                    val body = resp.body() ?: return@Thread
                    @Suppress("UNCHECKED_CAST")
                    val jobs = body["jobs"] as? List<Map<String, Any>> ?: return@Thread
                    for (job in jobs) {
                        try {
                            val json = JSONObject()
                            for ((k, v) in job) {
                                json.put(k, v?.toString() ?: "")
                            }
                            // JobManager-এর ডুপ্লিকেট চেক আছে
                            handler.post { jobManager.onJobReceived(json) }
                        } catch (_: Exception) { }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Poll failed: ${e.message}")
                ConnectionState.connected = false
            }
        }.start()
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()

        jobManager = JobManager(this)
        jobManager.listener = object : JobManager.Listener {
            override fun onJobLog(entry: JobManager.JobLogEntry) {
                JobLogStore.add(entry)
            }
        }

        socketManager = SocketManager(this)
        socketManager.listener = object : SocketManager.Listener {
            override fun onConnected() {
                ConnectionState.connected = true
            }
            override fun onDisconnected() {
                ConnectionState.connected = false
            }
            override fun onJobAssigned(job: JSONObject) {
                jobManager.onJobReceived(job)
            }
        }

        if (ServerConfig.isEnabled(this)) {
            socketManager.connect()
        }
        handler.post(heartbeatRunnable)
        handler.post(pollRunnable) // v3: HTTP polling শুরু
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RECONNECT -> {
                if (ServerConfig.isEnabled(this)) socketManager.connect()
            }
            ACTION_DISCONNECT -> {
                socketManager.disconnect()
                try {
                    val id = ServerConfig.getDeviceId(this)
                    if (id.isNotBlank()) ApiClient.get(this).disconnect(id).execute()
                } catch (_: Exception) { }
            }
            else -> {
                // v2 fix: default start-এও socket connect করো (service already running থাকলে onCreate হয় না)
                if (ServerConfig.isEnabled(this) && !socketManager.connected) {
                    socketManager.connect()
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(heartbeatRunnable)
        handler.removeCallbacks(pollRunnable)
        socketManager.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun sendHeartbeat() {
        if (!ServerConfig.isEnabled(this)) return
        try {
            val deviceId = ServerConfig.getDeviceId(this)
            if (deviceId.isBlank()) return
            Thread {
                try {
                    ApiClient.get(this).heartbeat(deviceId, "").execute()
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat failed: ${e.message}")
                }
            }.start()
        } catch (_: Exception) { }
    }

    private fun startForegroundNotification() {
        val channelId = "cockpit_server_driver"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(channelId, "Cockpit Server Driver", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Cockpit Server Driver")
            .setContentText("সার্ভারের সাথে সংযুক্ত — জবের অপেক্ষায়")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
        startForeground(1, notif)
    }

    companion object {
        private const val TAG = "CockpitHeartbeat"
        const val ACTION_RECONNECT = "com.bondhu.cockpitserver.RECONNECT"
        const val ACTION_DISCONNECT = "com.bondhu.cockpitserver.DISCONNECT"

        fun start(context: Context) {
            val i = Intent(context, HeartbeatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun reconnect(context: Context) {
            val i = Intent(context, HeartbeatService::class.java).apply { action = ACTION_RECONNECT }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, HeartbeatService::class.java))
        }
    }
}

/** UI-এর জন্য connection state + job log (in-memory)। */
object ConnectionState {
    @Volatile var connected: Boolean = false
}

object JobLogStore {
    private val logs = ArrayList<JobManager.JobLogEntry>()
    @Synchronized fun add(e: JobManager.JobLogEntry) {
        logs.add(0, e)
        while (logs.size > 20) logs.removeAt(logs.size - 1)
    }
    @Synchronized fun all(): List<JobManager.JobLogEntry> = logs.toList()
    @Synchronized fun clear() = logs.clear()
}
