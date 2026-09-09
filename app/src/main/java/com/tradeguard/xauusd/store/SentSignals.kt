package com.tradeguard.xauusd.store

import android.content.Context

/** Minimal key storage abstraction so dedup logic stays pure and testable. */
interface IdStore {
    fun contains(id: String): Boolean
    fun add(id: String)
}

/**
 * Tracks signal IDs that have already been notified. [tryMark] returns true
 * only the first time a given id is seen, which prevents duplicate alerts
 * for the same setup.
 */
class SentSignalRegistry(private val store: IdStore) {

    fun tryMark(id: String): Boolean {
        if (store.contains(id)) return false
        store.add(id)
        return true
    }
}

/** SharedPreferences-backed [IdStore] for sent signal IDs. */
class PrefsIdStore(
    context: Context,
    private val key: String = "sent_signal_ids"
) : IdStore {

    private val prefs = context.applicationContext
        .getSharedPreferences("tradeguard_prefs", Context.MODE_PRIVATE)

    override fun contains(id: String): Boolean =
        prefs.getStringSet(key, emptySet())?.contains(id) == true

    override fun add(id: String) {
        val current = prefs.getStringSet(key, emptySet())?.toMutableSet() ?: mutableSetOf()
        current.add(id)
        prefs.edit().putStringSet(key, current).apply()
    }
}
