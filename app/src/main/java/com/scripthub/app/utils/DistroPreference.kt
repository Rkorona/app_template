package com.scripthub.app.utils

import android.content.Context

enum class DistroType(val displayName: String, val id: String) {
    DEBIAN("Debian 12 (Bookworm)", "debian")
}

object DistroPreference {
    private const val PREF_NAME = "proot_prefs"
    private const val KEY_DISTRO  = "selected_distro"
    private const val KEY_SETUP   = "setup_done"

    fun getDistro(context: Context): DistroType {
        val id = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_DISTRO, DistroType.DEBIAN.id) ?: DistroType.DEBIAN.id
        return DistroType.entries.find { it.id == id } ?: DistroType.DEBIAN
    }

    fun setDistro(context: Context, distro: DistroType) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_DISTRO, distro.id).apply()
    }

    fun isSetupDone(context: Context): Boolean =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SETUP, false)

    fun markSetupDone(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SETUP, true).apply()
    }

    fun resetSetup(context: Context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SETUP, false).apply()
    }
}
