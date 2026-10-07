# Cockpit Server Driver

Separate server-connected automation driver for GP Cockpit ERS recharges.

- **Package:** `com.bondhu.cockpitserver`
- **App name:** Cockpit Server Driver
- **Engine:** ported from Cockpit UI Driver v35 (accessibility automation — fill number+amount,
  tap পাওয়ারলোড, সব পি.এল tab, scroll to exact-price offer, ERS PIN, confirm).
- **Server protocol:** same as SimSupport driver —
  Socket.IO (`recharge.assigned` on `device-<id>`), REST status reports
  (`POST /api/recharges/{id}/status`), 60s heartbeat, `device_id` = ANDROID_ID.

## Safety (non-negotiable)

1. **Job ID dedup** — processed job IDs persisted in SharedPreferences; the same ID is
   NEVER executed twice, even across restarts. Duplicate deliveries re-report the stored
   result without re-executing.
2. **Success only on success screen** — `completed` is reported only when the engine
   reaches the Cockpit success screen (`successCounted`).
3. **Transaction ID** — extracted from the success screen (`BD…` or 12+ digit ID),
   sent in the `sms` field of every completed report for reconciliation.
4. **5-minute per-job timeout** — stuck jobs report `failed` with reason `timeout`.
5. **Server-only** — no manual recharge UI; jobs execute only on server assignment.
6. **ERS PIN** never leaves the device (Android Keystore, same as manual driver).

## Project layout

```
app/src/main/java/com/bondhu/cockpitserver/
├── MainActivity.kt                 # server-only UI (status, settings, job log)
├── CockpitAccessibilityService.kt # automation engine (ported v35)
├── DriverSession.kt                # engine state (+ startServerJob, trxId)
├── DriverState.kt
├── SecureStore.kt                  # Keystore credentials
└── server/
    ├── ServerConfig.kt             # base/socket URL, device_id, enable flag
    ├── ApiClient.kt                # Retrofit singleton
    ├── ApiInterface.kt             # device + status endpoints
    ├── SocketManager.kt            # Socket.IO job channel
    ├── JobManager.kt               # validation → dedup → execute → report
    ├── HeartbeatService.kt         # foreground service + 60s heartbeat
    └── BootReceiver.kt
backend/                            # ADDITIVE server files (do not modify existing)
├── CockpitRouting.md
├── 2026_10_08_add_cockpit_support.php
├── CockpitDispatcher.php
└── config/cockpit.php
```

## Build

Via GitHub Actions (`.github/workflows/android-build.yml`) — same as manual driver.
Signed with its own persistent keystore (`app/debug-persistent.keystore`).

## Setup on phone

1. Install APK (separate from Cockpit UI Driver — different package).
2. Grant Accessibility permission.
3. Enter Cockpit ID / Password / ERS PIN → save (Keystore).
4. Set server URLs (defaults: shohozrecharge.com).
5. Enable "সার্ভার মোড" → Save & Connect.
6. Server admin sets AppConfig `cockpit_enabled=true` + `cockpit_mode`.
