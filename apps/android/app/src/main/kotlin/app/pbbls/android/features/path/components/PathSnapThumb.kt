package app.pbbls.android.features.path.components

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import app.pbbls.android.services.LocalSnapURLCache
import coil3.compose.AsyncImage

private const val TAG = "path-row-thumb"

/**
 * Lazily signs a thumb URL for one snap and renders it — the
 * `PathPebbleSnapThumb` analog. Sized, clipped, bordered and rotated by the
 * caller. A sign failure logs and leaves the transparent placeholder; a null
 * [LocalSnapURLCache] (screenshot previews) renders the placeholder without
 * any service plumbing.
 */
@Composable
fun PathSnapThumb(
    storagePath: String,
    modifier: Modifier = Modifier,
) {
    val cache = LocalSnapURLCache.current
    var url by remember(storagePath) { mutableStateOf<String?>(null) }

    LaunchedEffect(storagePath, cache) {
        val snapCache = cache ?: return@LaunchedEffect
        try {
            url = snapCache.signedUrls(storagePath).thumb
        } catch (e: Exception) {
            Log.e(TAG, "snap sign failed for $storagePath", e)
        }
    }

    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
    )
}
