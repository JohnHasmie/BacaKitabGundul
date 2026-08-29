package com.classicbookreader.app.core.cache

import com.classicbookreader.app.core.cache.LruPageCache.PageKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LruPageCacheTest {

    private fun cache(
        maxBytes: Long,
        onEvict: (PageKey, Long) -> Unit = { _, _ -> },
    ) = LruPageCache(maxBytes = maxBytes, sizeOf = { value: Long -> value }, onEvict = onEvict)

    @Test
    fun putAndGetRoundTrip() {
        val cache = cache(100)
        cache.put(PageKey(0, 1080), 10L)
        assertEquals(10L, cache.get(PageKey(0, 1080)))
        assertEquals(10L, cache.currentBytes)
        assertNull(cache.get(PageKey(1, 1080)))
    }

    @Test
    fun evictsLeastRecentlyUsedAndGetRefreshesRecency() {
        val evicted = mutableListOf<PageKey>()
        val cache = cache(30) { key, _ -> evicted += key }
        cache.put(PageKey(0, 1080), 10L)
        cache.put(PageKey(1, 1080), 10L)
        cache.put(PageKey(2, 1080), 10L)
        cache.get(PageKey(0, 1080)) // refresh page 0 → page 1 becomes LRU
        cache.put(PageKey(3, 1080), 10L)
        assertEquals(listOf(PageKey(1, 1080)), evicted)
        assertNotNull(cache.get(PageKey(0, 1080)))
        assertNull(cache.get(PageKey(1, 1080)))
    }

    @Test
    fun respectsByteBudgetAfterOversizedPut() {
        val cache = cache(25)
        cache.put(PageKey(0, 1080), 10L)
        cache.put(PageKey(1, 1080), 10L)
        cache.put(PageKey(2, 1080), 20L) // over budget → evicts 0 and 1
        assertEquals(1, cache.size)
        assertEquals(20L, cache.currentBytes)
        assertNotNull(cache.get(PageKey(2, 1080)))
    }

    @Test
    fun neverEvictsTheOnlyEntryEvenWhenOverBudget() {
        val cache = cache(5)
        cache.put(PageKey(0, 1080), 50L)
        assertEquals(1, cache.size)
        assertNotNull(cache.get(PageKey(0, 1080)))
    }

    @Test
    fun replacingAKeyAccountsBytesOnce() {
        val cache = cache(100)
        cache.put(PageKey(0, 1080), 10L)
        cache.put(PageKey(0, 1080), 30L)
        assertEquals(30L, cache.currentBytes)
        assertEquals(1, cache.size)
    }

    @Test
    fun clearZeroesEverything() {
        val cache = cache(100)
        cache.put(PageKey(0, 1080), 10L)
        cache.clear()
        assertEquals(0, cache.size)
        assertEquals(0L, cache.currentBytes)
    }
}
