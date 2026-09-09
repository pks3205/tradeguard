package com.tradeguard.xauusd

import com.tradeguard.xauusd.store.IdStore
import com.tradeguard.xauusd.store.SentSignalRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentSignalRegistryTest {

    private class InMemoryIdStore : IdStore {
        private val ids = HashSet<String>()
        override fun contains(id: String): Boolean = id in ids
        override fun add(id: String) { ids.add(id) }
    }

    @Test
    fun `duplicate alert ids are not sent twice`() {
        val registry = SentSignalRegistry(InMemoryIdStore())
        val id = "ORB_BUY:1704844800000"

        assertTrue(registry.tryMark(id))
        assertFalse(registry.tryMark(id))
        assertFalse(registry.tryMark(id))
    }

    @Test
    fun `distinct alert ids are each accepted exactly once`() {
        val registry = SentSignalRegistry(InMemoryIdStore())
        assertTrue(registry.tryMark("A"))
        assertTrue(registry.tryMark("B"))
        assertFalse(registry.tryMark("A"))
        assertTrue(registry.tryMark("C"))
    }
}
