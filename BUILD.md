# BUILD.md — Cockpit Server Driver v1

**Built:** 2026-10-07
**Repo:** `bondhusoftware/CockpitServer-Driver` (branch `main`)
**Package:** `com.bondhu.cockpitserver`
**App name:** Cockpit Server Driver

## What was built

Separate server-connected Android driver for GP Cockpit ERS recharges.

### Engine (ported from Cockpit UI Driver v35)
- `CockpitAccessibilityService.kt` — full automation: fill number+amount, tap পাওয়ারলোড,
  tap সব পি.এল tab, scroll to exact-price offer (Bengali digits), ERS PIN, confirm, OK.
- `DriverSession.kt` — + `startServerJob(jobId, phone, amount, usePlPath)`, `trxId`, `serverMode`.
- `DriverState.kt`, `SecureStore.kt` — unchanged (Keystore credentials).
- + `extractTrxId()` — pulls `BD…` / 12+ digit transaction ID from success screen.

### Server layer (new)
- `server/ServerConfig.kt` — URLs, device_id (ANDROID_ID), enable flag.
- `server/ApiClient.kt` + `ApiInterface.kt` — Retrofit: register/connect/disconnect/
  heartbeat/status + `POST /api/recharges/{id}/status`.
- `server/SocketManager.kt` — Socket.IO to `:6001`, subscribes `device-<id>`,
  handles `recharge.assigned`.
- `server/JobManager.kt` — validation → dedup (persisted) → execute → 5-min timeout →
  report. NEVER executes the same job ID twice.
- `server/HeartbeatService.kt` — foreground service, 60s heartbeat.
- `server/BootReceiver.kt` — restart server mode after reboot.
- `MainActivity.kt` — server-only UI: status, device ID, URLs, ERS credentials,
  job log (20), processed count. NO manual recharge input.

### Backend (additive only — in `backend/`)
- `CockpitRouting.md` — routing design doc (4 modes).
- `2026_10_08_add_cockpit_support.php` — migration (device_type, ers_balance).
- `CockpitDispatcher.php` — routing class, callable without modifying RechargeDispatcher.
- `config/cockpit.php` — config stub.

## Build status

⚠️ **APK not yet built** — the GitHub PAT lacks `workflow` scope, so
`.github/workflows/android-build.yml` could not be pushed via git/API.

**To complete:**
1. Via browser (user logged into GitHub): add `.github/workflows/android-build.yml`
   (content is in this repo locally) to `bondhusoftware/CockpitServer-Driver` on `main`.
   - OR: generate a new PAT with `workflow` scope and push the file.
2. Push triggers Actions → download `Cockpit-Server-Driver-debug-apk` artifact.
3. Save as `~/workspace/your_files/Cockpit-Server-Driver-v1.apk`.

The workflow file content (for manual add):
```yaml
name: Build Cockpit Server Driver APK
on:
  workflow_dispatch:
  push:
    branches: [ "main", "master" ]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '17' }
      - uses: gradle/actions/setup-gradle@v4
        with: { gradle-version: '8.7' }
      - run: gradle --no-daemon assembleDebug
      - uses: actions/upload-artifact@v4
        with:
          name: Cockpit-Server-Driver-debug-apk
          path: app/build/outputs/apk/debug/app-debug.apk
          if-no-files-found: error
```

## Safety checklist (implemented)

- [x] Job ID dedup persisted (SharedPreferences) — checked BEFORE execute
- [x] Duplicate delivery re-reports stored result, no re-execution
- [x] `completed` only when engine reaches success screen
- [x] Transaction ID in `sms` field of completed reports
- [x] 5-minute per-job timeout → `failed`
- [x] Server-only — no manual execution path
- [x] ERS PIN in Keystore, never sent to server
- [x] Single job at a time (no bulk in server mode)
- [x] Separate package + keystore from manual driver (isolation)
