# Cockpit Server Driver — Backend Integration (Additive Only)

> ⚠️ **Change-control:** এই ফাইলগুলো সব additive — existing backend ফাইলে কোনো modification নেই।
> প্রতিটা step-এর আগে backup + rollback plan নিশ্চিত করো।

## Routing Design

### Goal
GP রিচার্জ অর্ডার ৪টা মোডে রাউট করা যাবে (AppConfig `cockpit_mode`):

| Mode | GP Normal | GP PowerLoad | অন্য Operator |
|---|---|---|---|
| `off` (default) | USSD | USSD | USSD |
| `powerload_only` | USSD | **Cockpit** | USSD |
| `gp_all` | **Cockpit** | **Cockpit** | USSD |
| `both` | Cockpit→USSD fallback | Cockpit→USSD fallback | USSD |

### Flow
```
RechargeDispatcher (existing, untouched)
    │
    ├─► CockpitDispatcher::tryRoute($recharge)   ← NEW (called first)
    │       │
    │       ├─ cockpit_mode=off → return false (existing flow)
    │       ├─ operator != GP → return false
    │       ├─ No available cockpit device → return false (fall through to USSD)
    │       └─ Mode matches → assign to cockpit device (processing),
    │                         broadcast recharge.assigned with flow_type='cockpit'
    │                         return true
    │
    └─► existing USSD/vendor assignment (only if tryRoute returned false)
```

### Hook (existing RechargeDispatcher-এ ৩ লাইন যোগ — একমাত্র modification)
```php
// RechargeDispatcher::handle() — assignment loop-এর শুরুতে:
if (app(CockpitDispatcher::class)->tryRoute($recharge)) {
    continue; // cockpit took it
}
// ... existing USSD/vendor logic unchanged
```

বিকল্প (zero-modification): cron-এ CockpitDispatcher-কে আলাদা queued job হিসেবে
RechargeDispatcher-এর **আগে** চালাও — pending অর্ডার cockpit নিয়ে নিলে dispatcher skip করবে
(assigned_device_id IS NULL চেকের কারণে)।

### Safety
- **No double execution:** `tryRoute` uses `lockForUpdate` + `assigned_device_id IS NULL` check —
  একই অর্ডার দুই dispatcher নিতে পারবে না।
- **Fallback:** cockpit device unavailable/busy → `return false`, normal flow continues।
- **Money:** existing `updateStatus` ব্যবহার — commission/refund/cashback matrix অপরিবর্তিত।
- **Idempotency:** phone-side processed-ID set + server `oldStatus===newStatus` guard।

## Files
- `2026_10_08_add_cockpit_support.php` — migration: `sim_devices.device_type`, `flow_type` docs
- `CockpitDispatcher.php` — routing class (drop into `app/Services/`)
- `config/cockpit.php` — config stub (drop into `config/`)

## AppConfig keys (add via admin / tinker)
- `cockpit_enabled` = `true` (master switch)
- `cockpit_mode` = `off|powerload_only|gp_all|both` (default `off`)
- reuse existing `gp_powerload` CSV (e.g. `"19,29,98"`) for powerload detection
