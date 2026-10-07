<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Facades\DB;

/**
 * Cockpit Server Driver support — ADDITIVE ONLY.
 * - sim_devices.device_type: 'simsupport' (default) | 'cockpit'
 * - sim_devices.ers_balance: nullable decimal (retailer ERS balance tracking)
 *
 * Rollback: php artisan migrate:rollback (drops the two columns).
 * No existing columns/tables are modified.
 */
return new class extends Migration
{
    public function up(): void
    {
        Schema::table('sim_devices', function (Blueprint $table) {
            $table->string('device_type', 20)->default('simsupport')->after('device_name');
            $table->decimal('ers_balance', 12, 2)->nullable()->after('device_type');
        });

        // Backfill: existing SimSupport phones stay 'simsupport'.
        // Cockpit phones self-identify via POST /api/device/register {device_type:"cockpit"}.
        DB::table('sim_devices')->whereNull('device_type')->update(['device_type' => 'simsupport']);
    }

    public function down(): void
    {
        Schema::table('sim_devices', function (Blueprint $table) {
            $table->dropColumn(['device_type', 'ers_balance']);
        });
    }
};
