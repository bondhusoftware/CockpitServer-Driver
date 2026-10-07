package com.bondhu.cockpitserver

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import com.bondhu.cockpitserver.server.*

/**
 * Cockpit Server Driver (v1) — server-only।
 * ম্যানুয়াল রিচার্জ ইনপুট নেই; জব আসবে সার্ভার থেকে।
 */
class MainActivity : Activity() {

    private lateinit var statusTv: TextView
    private lateinit var deviceIdTv: TextView
    private lateinit var baseUrlInput: EditText
    private lateinit var socketUrlInput: EditText
    private lateinit var cockpitIdInput: EditText
    private lateinit var cockpitPasswordInput: EditText
    private lateinit var ersPinInput: EditText
    private lateinit var enableCheck: CheckBox
    private lateinit var logList: LinearLayout
    private lateinit var processedTv: TextView

    private val uiHandler = Handler(Looper.getMainLooper())
    private val uiRefresh = object : Runnable {
        override fun run() {
            refreshUi()
            uiHandler.postDelayed(this, 2000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        loadSettings()
    }

    override fun onResume() {
        super.onResume()
        refreshUi()
        uiHandler.post(uiRefresh)
    }

    override fun onPause() {
        uiHandler.removeCallbacks(uiRefresh)
        super.onPause()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
            setBackgroundColor(Color.WHITE)
        }

        root.addView(TextView(this).apply {
            text = "Cockpit Server Driver"
            textSize = 24f
            setTextColor(Color.rgb(20, 65, 95))
            gravity = Gravity.CENTER
        }, lp())

        root.addView(TextView(this).apply {
            text = "সার্ভার থেকে জব আসবে — ম্যানুয়াল রিচার্জ নেই।"
            textSize = 14f
            setPadding(0, 8, 0, 16)
            gravity = Gravity.CENTER
        }, lp())

        // 1. Accessibility
        root.addView(Button(this).apply {
            text = "১. Accessibility Permission"
            setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        }, lp())

        // 2. Server settings
        root.addView(TextView(this).apply {
            text = "সার্ভার সেটিংস"
            textSize = 18f
            setPadding(0, 16, 0, 4)
        }, lp())

        baseUrlInput = field("Base URL (https://shohozrecharge.com)", InputType.TYPE_CLASS_TEXT)
        root.addView(baseUrlInput, lp())

        socketUrlInput = field("Socket URL (http://shohozrecharge.com:6001)", InputType.TYPE_CLASS_TEXT)
        root.addView(socketUrlInput, lp())

        deviceIdTv = TextView(this).apply {
            textSize = 14f
            setPadding(0, 4, 0, 4)
        }
        root.addView(deviceIdTv, lp())

        enableCheck = CheckBox(this).apply {
            text = "সার্ভার মোড চালু (জব গ্রহণ করবে)"
        }
        root.addView(enableCheck, lp())

        root.addView(Button(this).apply {
            text = "💾 সেভ ও কানেক্ট"
            setOnClickListener { saveAndConnect() }
        }, lp())

        root.addView(Button(this).apply {
            text = "⏹️ সংযোগ বন্ধ"
            setOnClickListener { disconnectServer() }
        }, lp())

        // 3. ERS credentials (Keystore)
        root.addView(TextView(this).apply {
            text = "ERS Credentials (ডিভাইসে এনক্রিপ্টেড)"
            textSize = 18f
            setPadding(0, 16, 0, 4)
        }, lp())

        cockpitIdInput = field("Cockpit ID / POS", InputType.TYPE_CLASS_TEXT)
        root.addView(cockpitIdInput, lp())
        cockpitPasswordInput = passwordField("Cockpit Password")
        root.addView(cockpitPasswordInput, lp())
        ersPinInput = passwordField("ERS PIN")
        root.addView(ersPinInput, lp())

        root.addView(Button(this).apply {
            text = "🔐 Credentials সেভ"
            setOnClickListener { saveCredentials() }
        }, lp())

        // 4. Status
        statusTv = TextView(this).apply {
            textSize = 16f
            setPadding(0, 18, 0, 0)
        }
        root.addView(statusTv, lp())

        processedTv = TextView(this).apply {
            textSize = 14f
            setPadding(0, 4, 0, 0)
        }
        root.addView(processedTv, lp())

        // 5. Job log
        root.addView(TextView(this).apply {
            text = "📝 জব লগ (সর্বশেষ ২০)"
            textSize = 18f
            setPadding(0, 20, 0, 4)
        }, lp())

        logList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(logList, lp())

        root.addView(TextView(this).apply {
            text = "Security: ERS PIN কখনো সার্ভারে পাঠানো হয় না — শুধু এই ডিভাইসের Keystore-এ থাকে।"
            textSize = 13f
            setPadding(0, 16, 0, 0)
        }, lp())

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun loadSettings() {
        baseUrlInput.setText(ServerConfig.getBaseUrl(this))
        socketUrlInput.setText(ServerConfig.getSocketUrl(this))
        enableCheck.isChecked = ServerConfig.isEnabled(this)
        cockpitIdInput.setText(SecureStore.get(this, "cockpit_id"))
        cockpitPasswordInput.setText(SecureStore.get(this, "cockpit_password"))
        ersPinInput.setText(SecureStore.get(this, "ers_pin"))
        deviceIdTv.text = "Device ID: ${ServerConfig.getDeviceId(this)}"
    }

    private fun saveCredentials() {
        SecureStore.put(this, "cockpit_id", cockpitIdInput.text.toString())
        SecureStore.put(this, "cockpit_password", cockpitPasswordInput.text.toString())
        SecureStore.put(this, "ers_pin", ersPinInput.text.toString())
        toast("🔐 Credentials সেভ হয়েছে")
    }

    private fun saveAndConnect() {
        if (!isAccessibilityEnabled()) {
            toast("আগে Accessibility Permission দিন")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        if (cockpitIdInput.text.isBlank() || ersPinInput.text.isBlank()) {
            toast("Cockpit ID ও ERS PIN দিন")
            return
        }
        ServerConfig.setBaseUrl(this, baseUrlInput.text.toString())
        ServerConfig.setSocketUrl(this, socketUrlInput.text.toString())
        ServerConfig.setEnabled(this, enableCheck.isChecked)
        ApiClient.reset()
        saveCredentials()

        if (enableCheck.isChecked) {
            HeartbeatService.start(this)
            // register now (upsert device_type=cockpit)
            Thread {
                try {
                    val id = ServerConfig.getDeviceId(this)
                    if (id.isNotBlank()) {
                        ApiClient.get(this).register(id, ServerConfig.getDeviceName(), "cockpit").execute()
                    }
                } catch (_: Exception) { }
            }.start()
            toast("✅ সার্ভার মোড চালু — জবের অপেক্ষায়")
        } else {
            HeartbeatService.stop(this)
            toast("⏹️ সার্ভার মোড বন্ধ")
        }
        refreshUi()
    }

    private fun disconnectServer() {
        ServerConfig.setEnabled(this, false)
        enableCheck.isChecked = false
        HeartbeatService.reconnect(this) // sends disconnect action
        HeartbeatService.stop(this)
        Thread {
            try {
                val id = ServerConfig.getDeviceId(this)
                if (id.isNotBlank()) ApiClient.get(this).disconnect(id).execute()
            } catch (_: Exception) { }
        }.start()
        toast("⏹️ সংযোগ বন্ধ")
        refreshUi()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val expected = ComponentName(this, CockpitAccessibilityService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    private fun refreshUi() {
        val enabled = ServerConfig.isEnabled(this)
        val conn = if (ConnectionState.connected) "🟢 সংযুক্ত" else "🔴 সংযোগ বিচ্ছিন্ন"
        val a11y = if (isAccessibilityEnabled()) "✅" else "❌"
        statusTv.text = "Status: $conn\nসার্ভার মোড: ${if (enabled) "চালু" else "বন্ধ"}\nAccessibility: $a11y\nEngine: ${DriverSession.state} — ${DriverSession.lastMessage}"

        val prefs = getSharedPreferences("cockpit_processed_jobs", MODE_PRIVATE)
        val count = prefs.all.keys.count { it.startsWith("result_") }
        processedTv.text = "প্রসেসড জব: $count"

        renderLog()
    }

    private fun renderLog() {
        logList.removeAllViews()
        val logs = JobLogStore.all()
        if (logs.isEmpty()) {
            logList.addView(TextView(this).apply {
                text = "এখনো কোনো জব নেই"
                textSize = 14f
                setTextColor(Color.GRAY)
            })
            return
        }
        for (e in logs) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(4, 10, 4, 10)
                isClickable = true
            }
            row.addView(TextView(this).apply {
                text = "${e.time}  ${e.phone} • ${e.amount} TK"
                textSize = 15f
            })
            row.addView(TextView(this).apply {
                text = "${e.status} — ${e.detail}"
                textSize = 13f
                setTextColor(Color.DKGRAY)
            })
            row.setOnClickListener {
                AlertDialog.Builder(this)
                    .setTitle("📝 জব")
                    .setMessage("Job ID: ${e.jobId}\nনম্বর: ${e.phone}\nপরিমাণ: ${e.amount} TK\nস্ট্যাটাস: ${e.status}\nবিস্তারিত: ${e.detail}\nসময়: ${e.time}")
                    .setPositiveButton("ঠিক আছে", null)
                    .show()
            }
            logList.addView(row)
            val div = android.view.View(this).apply {
                setBackgroundColor(Color.rgb(230, 230, 230))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 2)
            }
            logList.addView(div)
        }
    }

    private fun field(hint: String, type: Int) = EditText(this).apply {
        this.hint = hint
        inputType = type
        textSize = 16f
    }

    private fun passwordField(hint: String) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        textSize = 16f
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun lp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { setMargins(0, 6, 0, 6) }
}
