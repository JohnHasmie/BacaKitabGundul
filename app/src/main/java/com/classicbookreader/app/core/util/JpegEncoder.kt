package com.classicbookreader.app.core.util

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/**
 * Encodes a bitmap as JPEG under a byte budget by stepping quality down
 * (85 → 40); the analysis crop budgets 300KB, a full page ~500KB.
 */
fun encodeJpeg(bitmap: Bitmap, maxBytes: Int): ByteArray {
    var quality = 85
    while (true) {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        val bytes = stream.toByteArray()
        if (bytes.size <= maxBytes || quality <= 40) return bytes
        quality -= 15
    }
}
