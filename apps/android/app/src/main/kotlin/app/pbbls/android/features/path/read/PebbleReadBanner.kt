package app.pbbls.android.features.path.read

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.services.LocalSnapURLCache
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap

private const val TAG = "pebble-read-banner"

/** Slot for the Petroglyph when it is the page heading (no snap). */
private val HEADING_PETROGLYPH = 150.dp

/** The decoded snap plus its bucketed frame, resolved before layout. */
private data class BannerPhoto(
    val bitmap: ImageBitmap,
    val aspect: BannerAspect,
)

/**
 * Top zone of the pebble read view — the #599 redesign.
 *
 * - **No snap** (or one that hasn't loaded / failed to load): the framed
 *   [PebbleReadPetroglyph] centred as the page heading.
 * - **Snap present**: once the ORIGINAL rendition is signed + decoded, the whole
 *   picture shows at its nearest [BannerAspect] bucket with the Petroglyph
 *   overlapping its top-right ([PebbleSnapFrame]).
 *
 * This replaces the previous two-phase reveal (the snap sliding in over the
 * pebble after the stroke animation, M42 #582): the page now frames the picture
 * outright, so there is no mask/slide. A failed load simply stays on the
 * Petroglyph heading — no error UI — and the pebble render is static on Android,
 * so nothing gates the swap but the decode itself.
 */
@Composable
fun PebbleReadBanner(
    renderSvg: String?,
    valence: Valence,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
    snapStoragePath: String? = null,
) {
    val context = LocalContext.current
    val snapUrls = LocalSnapURLCache.current
    var photo by remember(snapStoragePath) { mutableStateOf<BannerPhoto?>(null) }

    LaunchedEffect(snapStoragePath, snapUrls) {
        photo = null
        if (snapStoragePath == null || snapUrls == null) return@LaunchedEffect
        try {
            val url = snapUrls.signedUrls(snapStoragePath).original
            val result =
                SingletonImageLoader
                    .get(context)
                    .execute(ImageRequest.Builder(context).data(url).build())
            val bitmap = (result as? SuccessResult)?.image?.toBitmap()
            if (bitmap != null) {
                val ratio = bitmap.width.toFloat() / maxOf(bitmap.height, 1)
                photo = BannerPhoto(bitmap.asImageBitmap(), BannerAspect.nearest(ratio))
            } else {
                Log.w(TAG, "photo load failed for $snapStoragePath")
            }
        } catch (e: Exception) {
            Log.w(TAG, "photo load failed for $snapStoragePath", e)
        }
    }

    val loaded = photo
    if (loaded == null) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            PebbleReadPetroglyph(
                renderSvg = renderSvg,
                valence = valence,
                palette = palette,
                modifier = Modifier.size(HEADING_PETROGLYPH),
            )
        }
    } else {
        PebbleSnapFrame(
            photo = remember(loaded.bitmap) { BitmapPainter(loaded.bitmap) },
            aspect = loaded.aspect,
            renderSvg = renderSvg,
            valence = valence,
            palette = palette,
            modifier = modifier,
        )
    }
}
