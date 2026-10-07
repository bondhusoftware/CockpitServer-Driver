package com.bondhu.cockpitserver.server

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Server Driver (v1): রিবুটের পর সার্ভার মোড আবার চালু করো (যদি enabled থাকে)।
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (ServerConfig.isEnabled(context)) {
                HeartbeatService.start(context)
            }
        }
    }
}
