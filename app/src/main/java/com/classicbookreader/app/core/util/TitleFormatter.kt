package com.classicbookreader.app.core.util

/** Derives a human-friendly book title from a picked file's display name. */
object TitleFormatter {

    private val extension = Regex("\\.pdf$", RegexOption.IGNORE_CASE)
    private val separators = Regex("[-_]+")
    private val whitespace = Regex("\\s+")

    fun deriveTitle(displayName: String?, fallback: String): String {
        val cleaned = (displayName ?: "")
            .replace(extension, "")
            .replace(separators, " ")
            .replace(whitespace, " ")
            .trim()
        return cleaned.ifBlank { fallback }
    }
}
