package com.classicbookreader.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleFormatterTest {

    @Test
    fun stripsExtensionCaseInsensitively() {
        assertEquals("fathul qorib", TitleFormatter.deriveTitle("fathul-qorib.PDF", "x"))
        assertEquals("jurumiyah", TitleFormatter.deriveTitle("jurumiyah.pdf", "x"))
    }

    @Test
    fun replacesSeparatorsAndCollapsesWhitespace() {
        assertEquals("matan al ajurumiyyah v2", TitleFormatter.deriveTitle("matan_al-ajurumiyyah__v2.pdf", "x"))
        assertEquals("kitab tauhid", TitleFormatter.deriveTitle("  kitab   tauhid  ", "x"))
    }

    @Test
    fun keepsNameWithoutExtension() {
        assertEquals("bulughul maram", TitleFormatter.deriveTitle("bulughul maram", "x"))
    }

    @Test
    fun fallsBackWhenBlankOrNull() {
        assertEquals("Kitab tanpa judul", TitleFormatter.deriveTitle(null, "Kitab tanpa judul"))
        assertEquals("Kitab tanpa judul", TitleFormatter.deriveTitle("   ", "Kitab tanpa judul"))
        assertEquals("Kitab tanpa judul", TitleFormatter.deriveTitle(".pdf", "Kitab tanpa judul"))
    }
}
