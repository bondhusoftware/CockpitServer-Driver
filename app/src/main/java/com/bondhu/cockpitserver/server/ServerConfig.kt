package com.bondhu.cockpitserver.server

import android.content.Context
import android.provider.Settings

/**
 * Server Driver (v1): সার্ভার কানেকশন সেটিংস।
 * device_id = ANDROID_ID (SimSupport-এর মতোই)।
 */
object ServerConfig {
    private const val PREFS = "cockpit_server_config"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_SOCKET_URL = "socket_url"
    private const val KEY_ENABLED = "server_enabled"

    const val DEFAULT_BASE_URL = "https://shohozrecharge.com"
    const val DEFAULT_SOCKET_URL = "http://shohozrecharge.com:6001"

    fun getBaseUrl(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
    }

    fun setBaseUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_BASE_URL, url.trim().trimEnd('/')).apply()
    }

    fun getSocketUrl(context: Context): String {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SOCKET_URL, DEFAULT_SOCKET_URL) ?: DEFAULT_SOCKET_URL
    }

    fun setSocketUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SOCKET_URL, url.trim().trimEnd('/')).apply()
    }

    fun isEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** SimSupport-এর মতো device_id = ANDROID_ID */
    fun getDeviceId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()
        } catch (_: Exception) {
            ""
        }
    }

    fun getDeviceName(): String = "COCKPIT-GP-01"
}
