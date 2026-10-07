package com.bondhu.cockpitserver.server

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.bondhu.cockpitserver.CockpitAccessibilityService
import com.bondhu.cockpitserver.DriverSession
import com.bondhu.cockpitserver.DriverState
import org.json.JSONObject

/**
 * Server Driver (v1): জব গ্রহণ → যাচাই → dedup → engine-এ চালানো → ফলাফল রিপোর্ট।
 *
 * Safety (non-negotiable):
 * - একই job ID দুইবার execute হবে না (persisted processed map)।
 * - success রিপোর্ট শুধু engine আসলেই success স্ক্রিনে পৌঁছালে।
 * - completed রিপোর্টে Cockpit transaction ID থাকবে।
 * - 5 মিনিট timeout — আটকে থাকলে failed রিপোর্ট।
 */
class JobManager(private val context: Context) {

    interface Listener {
        fun onJobLog(entry: JobLogEntry)
    }

    data class JobLogEntry(
        val jobId: String,
        val phone: String,
        val amount: String,
        val status: String,
        val detail: String,
        val time: String
    )

    var listener: Listener? = null
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = context.getSharedPreferences(PREFS_PROCESSED, Context.MODE_PRIVATE)

    @Volatile private var currentJobId: String? = null
    @Volatile private var jobStartAt: Long = 0L

    companion object {
        private const val TAG = "CockpitJobManager"
        private const val PREFS_PROCESSED = "cockpit_processed_jobs"
        private const val JOB_TIMEOUT_MS = 5 * 60 * 1000L  // 5 মিনিট
        private const val POLL_MS = 2000L
    }

    /** SocketManager থেকে জব এলে */
    fun onJobReceived(job: JSONObject) {
        val jobId = job.optString("id").trim()
        val phone = job.optString("phone_number").trim()
        val amount = job.optString("amount").trim()
        val operator = job.optString("operator_type").trim().uppercase()
        val flowType = job.optString("flow_type").trim().lowercase()

        Log.i(TAG, "Job: id=$jobId phone=$phone amount=$amount op=$operator flow=$flowType")

        // 1. Validation
        if (jobId.isBlank() || phone.isBlank() || amount.isBlank()) {
            log(jobId.ifBlank { "?" }, phone, amount, "⛔ বাতিল", "অসম্পূর্ণ জব")
            return
        }
        // শুধু GP / cockpit flow — অন্য operator এলে skip (server-এর routing-এর বাইরে)
        val isCockpitFlow = flowType == "cockpit" || flowType == "powerload"
        if (operator != "GP" && !isCockpitFlow) {
            log(jobId, phone, amount, "⛔ বাতিল", "operator=$operator (GP নয়)")
            reportStatus(jobId, "failed", "", "unsupported operator: $operator")
            return
        }

        // 2. Dedup — আগেই process করা হয়েছে কিনা
        val prevResult = prefs.getString("result_$jobId", null)
        if (prevResult != null) {
            Log.i(TAG, "Job $jobId already processed ($prevResult) — skip execution, re-report")
            log(jobId, phone, amount, "↩️ ডুপ্লিকেট", "আগের রেজাল্ট: $prevResult")
            // Server-কে আবার জানাও (idempotent) — কিন্তু execute নয়
            val parts = prevResult.split("|", limit = 2)
            reportStatus(jobId, parts[0], parts.getOrElse(1) { "" }, "duplicate-delivery")
            return
        }

        // 3. Busy check — একসাথে একটাই জব
        if (currentJobId != null || DriverSession.running) {
            log(jobId, phone, amount, "⏳ অপেক্ষায়", "আরেকটা জব চলছে")
            // Server আবার পাঠাবে (processing-এ আটকে থাকলে dispatcher re-run করে)
            return
        }

        // 4. Execute
        currentJobId = jobId
        jobStartAt = System.currentTimeMillis()
        val usePlPath = isCockpitFlow
        log(jobId, phone, amount, "🔄 চলছে", if (usePlPath) "⚡ পাওয়ারলোড" else "normal")

        DriverSession.startServerJob(jobId, phone, amount, usePlPath)

        if (!launchCockpit()) {
            finishJob(jobId, "failed", "", "Cockpit app not found")
            return
        }

        // 5. Monitor
        handler.postDelayed({ pollJob(jobId) }, POLL_MS)
    }

    private fun pollJob(jobId: String) {
        if (currentJobId != jobId) return  // অন্য জব বা cancel

        // Timeout?
        if (System.currentTimeMillis() - jobStartAt > JOB_TIMEOUT_MS) {
            Log.w(TAG, "Job $jobId timeout")
            DriverSession.stop("⏱️ timeout — 5 মিনিটে শেষ হয়নি")
            finishJob(jobId, "failed", DriverSession.trxId, "timeout after 5 min")
            return
        }

        // এখনো চলছে?
        if (DriverSession.running) {
            handler.postDelayed({ pollJob(jobId) }, POLL_MS)
            return
        }

        // শেষ — ফলাফল নির্ধারণ
        val completed = DriverSession.completedCount > 0 && DriverSession.failedCount == 0
        val trx = DriverSession.trxId
        if (completed) {
            val reason = buildReason(jobId, true, trx)
            finishJob(jobId, "completed", trx, reason)
        } else {
            val reason = buildReason(jobId, false, trx)
            finishJob(jobId, "failed", trx, reason)
        }
    }

    private fun buildReason(jobId: String, success: Boolean, trx: String): String {
        val path = if (DriverSession.isPlBatch) "powerload" else "normal"
        val base = "$path ${DriverSession.amount}TK"
        val msg = DriverSession.lastMessage.take(120)
        return if (success) {
            "$base OK trx=$trx | $msg"
        } else {
            "$base FAILED | $msg"
        }
    }

    /** জব শেষ — persist + report + cleanup */
    private fun finishJob(jobId: String, status: String, trxId: String, reason: String) {
        // Persist BEFORE report — crash হলেও double-execute হবে না
        prefs.edit()
            .putString("result_$jobId", "$status|$trxId")
            .putLong("time_$jobId", System.currentTimeMillis())
            .apply()
        // Trim: 200-এর বেশি রাখো না
        trimProcessed()

        reportStatus(jobId, status, trxId, reason)

        val st = when (status) {
            "completed" -> "✅ সফল"
            "failed" -> "❌ ব্যর্থ"
            else -> status
        }
        log(jobId, DriverSession.phone, DriverSession.amount, st, reason.take(80))
        currentJobId = null
    }

    private fun reportStatus(jobId: String, status: String, sms: String, reason: String) {
        try {
            val deviceId = ServerConfig.getDeviceId(context)
            val resp = ApiClient.get(context).reportRechargeStatus(
                jobId, status, sms, reason, "cockpit"
            ).execute()
            Log.i(TAG, "Report $jobId=$status → HTTP ${resp.code()}")
        } catch (e: Exception) {
            Log.w(TAG, "Report failed for $jobId: ${e.message}")
            // Server unreachable — processed map-এ আছে, retry হবে না (server re-assign করলে dedup ধরবে)
        }
    }

    private fun trimProcessed() {
        try {
            val keys = prefs.all.keys.filter { it.startsWith("result_") }
            if (keys.size > 200) {
                // সবচেয়ে পুরনোগুলো মুছো
                val times = keys.mapNotNull { k ->
                    val id = k.removePrefix("result_")
                    val t = prefs.getLong("time_$id", 0)
                    if (t > 0) id to t else null
                }.sortedBy { it.second }
                val toRemove = times.take(keys.size - 200)
                val ed = prefs.edit()
                for ((id, _) in toRemove) {
                    ed.remove("result_$id")
                    ed.remove("time_$id")
                }
                ed.apply()
            }
        } catch (_: Exception) { }
    }

    private fun launchCockpit(): Boolean {
        return try {
            val pm: PackageManager = context.packageManager
            val known = listOf("retail.grameenphone.com.gpretail", "com.grameenphone.cockpit")
            for (pkg in known) {
                try {
                    val launch = pm.getLaunchIntentForPackage(pkg)
                    if (launch != null) {
                        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        context.startActivity(launch)
                        return true
                    }
                } catch (_: Exception) { }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun log(jobId: String, phone: String, amount: String, status: String, detail: String) {
        val time = try {
            java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        } catch (_: Exception) { "" }
        listener?.onJobLog(JobLogEntry(jobId, phone, amount, status, detail, time))
    }

    /** বাইরে থেকে cancel (যেমন: server disconnect) */
    fun cancelCurrent(reason: String) {
        val jobId = currentJobId ?: return
        DriverSession.stop(reason)
        finishJob(jobId, "failed", DriverSession.trxId, "cancelled: $reason")
    }

    fun isBusy(): Boolean = currentJobId != null
}
