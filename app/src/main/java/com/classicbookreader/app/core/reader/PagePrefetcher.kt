package com.classicbookreader.app.core.reader

/**
 * Pages to keep warm around [current]: the current page first, then
 * neighbours by distance, clamped to the document bounds.
 */
fun prefetchWindow(current: Int, pageCount: Int, radius: Int = 2): List<Int> {
    if (pageCount <= 0) return emptyList()
    val clampedCurrent = current.coerceIn(0, pageCount - 1)
    val window = mutableListOf(clampedCurrent)
    for (distance in 1..radius) {
        val next = clampedCurrent + distance
        val previous = clampedCurrent - distance
        if (next < pageCount) window += next
        if (previous >= 0) window += previous
    }
    return window
}
