package com.classicbookreader.app.core.selection

import java.security.MessageDigest
import kotlin.math.roundToInt

/**
 * Stable cache key for an analysis: same book, page, and (quantized)
 * selection → same key, so re-circling never pays for a second model call.
 * The bbox is quantized to a 1/200 grid — retracing the same word lands on
 * the same key even when the stroke wobbles a few pixels.
 */
object AnalysisCacheKey {

    private const val GRID = 200f

    fun forSelection(bookId: Long, pageIndex: Int, selection: NormalizedRect): String {
        val quantized = listOf(selection.left, selection.top, selection.right, selection.bottom)
            .joinToString(",") { (it * GRID).roundToInt().toString() }
        return sha256("$bookId|$pageIndex|$quantized")
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }
}
