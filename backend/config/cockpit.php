<?php

/**
 * Cockpit Server Driver — config stub.
 * Copy to <laravel>/config/cockpit.php (or set via AppConfig keys — the
 * CockpitDispatcher reads AppConfig first; this file documents defaults).
 */
return [
    // Master switch — false = existing USSD/vendor behavior only.
    'enabled' => env('COCKPIT_ENABLED', false),

    // off | powerload_only | gp_all | both
    'mode' => env('COCKPIT_MODE', 'off'),

    // Per-job timeout the phone enforces (seconds). Server watchdogs only
    // auto-fail *unassigned* pending, so this is informational here.
    'job_timeout_seconds' => 300,

    // Reuse existing AppConfig 'gp_powerload' CSV for powerload detection.
    // Example: "19,29,98"
];
