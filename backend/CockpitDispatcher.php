<?php

namespace App\Services;

use App\Events\RechargeAssigned;
use App\Models\AppConfig;
use App\Models\Recharge;
use App\Models\SimDevice;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;

/**
 * Cockpit Server Driver — routing for GP Cockpit automation phones.
 *
 * ADDITIVE: designed to be called from RechargeDispatcher WITHOUT modifying it:
 *
 *   // In RechargeDispatcher::handle(), before the existing assign block:
 *   if (app(CockpitDispatcher::class)->tryRoute($recharge)) {
 *       continue;
 *   }
 *
 * Modes (AppConfig `cockpit_mode`):
 *   off            — do nothing (default; existing behavior)
 *   powerload_only — only GP powerload amounts → cockpit
 *   gp_all         — all GP → cockpit
 *   both           — all GP → cockpit first, USSD fallback handled by caller
 *                    (this method returns false when no cockpit device is free,
 *                     so the existing dispatcher assigns to USSD)
 */
class CockpitDispatcher
{
    /**
     * Try to route a pending recharge to a cockpit device.
     * Returns true if routed (caller should skip normal assignment).
     */
    public function tryRoute(Recharge $recharge): bool
    {
        try {
            if (AppConfig::get('cockpit_enabled', 'false') !== 'true') {
                return false;
            }

            $mode = AppConfig::get('cockpit_mode', 'off');
            if ($mode === 'off') {
                return false;
            }

            // Only GP goes to cockpit (Cockpit app is GP ERS).
            if (strtoupper($recharge->operator_type) !== 'GP') {
                return false;
            }

            $isPowerload = $this->isPowerloadAmount($recharge->amount);

            if ($mode === 'powerload_only' && !$isPowerload) {
                return false;
            }
            // gp_all / both: all GP amounts are eligible.

            return DB::transaction(function () use ($recharge) {
                // Re-check pending + unassigned inside the lock (no double-assign).
                $fresh = Recharge::where('id', $recharge->id)
                    ->where('status', 'pending')
                    ->whereNull('assigned_device_id')
                    ->lockForUpdate()
                    ->first();
                if (!$fresh) {
                    return false;
                }

                $device = SimDevice::where('device_type', 'cockpit')
                    ->where('status', 'available')
                    ->where('can_recharge', true)
                    ->lockForUpdate()
                    ->first();
                if (!$device) {
                    return false; // fall through to USSD/vendor
                }

                $fresh->assigned_device_id = $device->device_id ?? $device->id;
                $fresh->status = 'processing';
                $fresh->save();

                $device->status = 'busy';
                $device->save();

                // Mirror to all_history if your dispatcher does so (kept consistent).
                try {
                    \App\Models\AllHistory::where('recharge_id', $fresh->id)
                        ->update(['status' => 'processing']);
                } catch (\Throwable $e) {
                    // table/model may differ; non-fatal
                }

                event(new RechargeAssigned($fresh, $device, 'cockpit'));

                Log::info("CockpitDispatcher: routed recharge {$fresh->id} to cockpit device {$device->id}");
                return true;
            });
        } catch (\Throwable $e) {
            Log::warning('CockpitDispatcher::tryRoute failed: ' . $e->getMessage());
            return false; // never break the existing flow
        }
    }

    /**
     * Amount in gp_powerload CSV? (reuses existing AppConfig key, e.g. "19,29,98")
     */
    public function isPowerloadAmount($amount): bool
    {
        $csv = AppConfig::get('gp_powerload', '');
        if (trim($csv) === '') {
            return false;
        }
        $norm = $this->normalizeAmount($amount);
        $list = collect(explode(',', $csv))
            ->map(fn($v) => $this->normalizeAmount(trim($v)))
            ->filter()
            ->all();
        return in_array($norm, $list, true);
    }

    private function normalizeAmount($a): string
    {
        $a = trim((string) $a);
        if (!is_numeric($a)) {
            return $a;
        }
        $d = (float) $a;
        return ((string)(int)$d === (string)$d || $d == (int)$d) ? (string)(int)$d : $a;
    }
}
