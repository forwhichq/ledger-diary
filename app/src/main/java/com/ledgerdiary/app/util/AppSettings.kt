package com.ledgerdiary.app.util

import android.content.Context

/**
 * 极轻量的本地设置。用 SharedPreferences 而不是 DataStore，是因为这里只有三五个开关，
 * 没必要为此引入额外依赖和协程样板。
 */
object AppSettings {
    private const val FILE = "settings"
    private const val KEY_LOCK = "app_lock"
    private const val KEY_ONBOARDED = "onboarded"

    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isLockEnabled(c: Context): Boolean = sp(c).getBoolean(KEY_LOCK, false)
    fun setLockEnabled(c: Context, v: Boolean) {
        sp(c).edit().putBoolean(KEY_LOCK, v).apply()
    }

    fun hasOnboarded(c: Context): Boolean = sp(c).getBoolean(KEY_ONBOARDED, false)
    fun setOnboarded(c: Context) {
        sp(c).edit().putBoolean(KEY_ONBOARDED, true).apply()
    }
}
