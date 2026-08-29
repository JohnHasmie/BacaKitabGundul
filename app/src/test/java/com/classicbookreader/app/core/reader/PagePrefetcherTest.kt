package com.classicbookreader.app.core.reader

import org.junit.Assert.assertEquals
import org.junit.Test

class PagePrefetcherTest {

    @Test
    fun midDocumentWindowOrdersByDistance() {
        assertEquals(listOf(5, 6, 4, 7, 3), prefetchWindow(current = 5, pageCount = 40))
    }

    @Test
    fun clampsAtDocumentStart() {
        assertEquals(listOf(0, 1, 2), prefetchWindow(current = 0, pageCount = 40))
    }

    @Test
    fun clampsAtDocumentEnd() {
        assertEquals(listOf(39, 38, 37), prefetchWindow(current = 39, pageCount = 40))
    }

    @Test
    fun clampsOutOfRangeCurrent() {
        assertEquals(listOf(9, 8, 7), prefetchWindow(current = 99, pageCount = 10))
    }

    @Test
    fun singlePageAndEmptyDocuments() {
        assertEquals(listOf(0), prefetchWindow(current = 0, pageCount = 1))
        assertEquals(emptyList<Int>(), prefetchWindow(current = 0, pageCount = 0))
    }
}
