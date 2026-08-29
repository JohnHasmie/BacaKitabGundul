package com.classicbookreader.app.core.cache

/**
 * Byte-budgeted LRU cache for rendered page bitmaps, kept as pure Kotlin so
 * the eviction policy is unit-testable on the JVM. Values are keyed by
 * (pageIndex, widthPx): a viewport-width change simply creates new keys and
 * the budget evicts the stale-width entries.
 *
 * Not thread-safe by itself — callers confine access to one context
 * (the ViewModel does, via its render mutex).
 */
class LruPageCache<V : Any>(
    private val maxBytes: Long,
    private val sizeOf: (V) -> Long,
    private val onEvict: (key: PageKey, value: V) -> Unit = { _, _ -> },
) {
    data class PageKey(val pageIndex: Int, val widthPx: Int)

    private val entries = LinkedHashMap<PageKey, V>(16, 0.75f, true)

    var currentBytes: Long = 0
        private set

    val size: Int get() = entries.size

    fun get(key: PageKey): V? = entries[key]

    fun put(key: PageKey, value: V) {
        entries.remove(key)?.let { currentBytes -= sizeOf(it) }
        entries[key] = value
        currentBytes += sizeOf(value)
        evictIfNeeded()
    }

    fun clear() {
        entries.clear()
        currentBytes = 0
    }

    private fun evictIfNeeded() {
        val iterator = entries.entries.iterator()
        while (currentBytes > maxBytes && iterator.hasNext()) {
            val eldest = iterator.next()
            if (entries.size == 1) break // never evict the only (current) entry
            iterator.remove()
            currentBytes -= sizeOf(eldest.value)
            onEvict(eldest.key, eldest.value)
        }
    }
}
