package com.tradeguard.xauusd

import com.tradeguard.xauusd.model.ChecklistCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChecklistCatalogTest {

    @Test
    fun `every rule id is unique`() {
        val ids = ChecklistCatalog.allRules().map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `both setups provide buy and sell checklists`() {
        assertEquals(2, ChecklistCatalog.setups.size)
        ChecklistCatalog.setups.forEach { setup ->
            assertTrue("${setup.id} has no BUY rules", setup.buy.isNotEmpty())
            assertTrue("${setup.id} has no SELL rules", setup.sell.isNotEmpty())
        }
    }

    @Test
    fun `every checklist ends with the minimum risk reward rule`() {
        ChecklistCatalog.setups.forEach { setup ->
            assertTrue(setup.buy.last().label.contains("1:2"))
            assertTrue(setup.sell.last().label.contains("1:2"))
        }
    }

    @Test
    fun `buy and sell lists are direction specific`() {
        val setup = ChecklistCatalog.setups.first()
        assertTrue(setup.buy.first().label.contains("swing low"))
        assertTrue(setup.sell.first().label.contains("swing high"))
        assertNotEquals(setup.buy.first().id, setup.sell.first().id)
    }
}
