package com.tradeguard.xauusd.store

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists which checklist rules the trader has checked, using SharedPreferences
 * (one boolean key per rule id).
 */
class ChecklistStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("tradeguard_checklist", Context.MODE_PRIVATE)

    fun isChecked(ruleId: String): Boolean = prefs.getBoolean(ruleId, false)

    fun setChecked(ruleId: String, checked: Boolean) =
        prefs.edit().putBoolean(ruleId, checked).apply()

    fun clear(ruleId: String) = prefs.edit().remove(ruleId).apply()
}
