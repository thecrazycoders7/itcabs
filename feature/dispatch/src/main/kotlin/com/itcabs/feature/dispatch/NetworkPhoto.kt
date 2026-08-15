package com.itcabs.feature.dispatch

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * A circular photo loaded from a public URL (driver face photo). No image library — a bounded-decode
 * off the IO dispatcher keeps it dependency-free and OOM-safe. Falls back to an emoji/initial.
 */
@Composable
fun NetworkPhoto(url: String?, size: Dp = 56.dp, fallback: String = "🧑") {
    val box = Modifier.size(size).clip(CircleShape)
    val bmp by produceState<Bitmap?>(initialValue = null, url) {
        value = if (url.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            runCatching {
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                URL(url).openStream().use { BitmapFactory.decodeStream(it, null, opts) }
            }.getOrNull()
        }
    }
    val b = bmp
    if (b != null) {
        Image(b.asImageBitmap(), contentDescription = "Driver photo", modifier = box, contentScale = ContentScale.Crop)
    } else {
        Box(box.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text(fallback, style = MaterialTheme.typography.titleLarge)
        }
    }
}
