package app.pbbls.android.features.path.read

import android.provider.Settings
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pbbls.android.features.path.models.EmotionPalette
import app.pbbls.android.features.path.models.Valence
import app.pbbls.android.features.path.models.ValenceSizeGroup
import app.pbbls.android.features.path.render.PebbleStaticRender
import app.pbbls.android.services.LocalSnapURLCache
import app.pbbls.android.theme.PebblesTheme
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap

private const val TAG = "pebble-read-banner"

/** The decoded photo + its bucketed frame, resolved before layout (design D7). */
private data class BannerPhoto(
    val bitmap: ImageBitmap,
    val aspect: BannerAspect,
)

/**
 * Render banner for the detail sheet — ports iOS `PebbleReadBanner.swift`,
 * now with the two-phase photo reveal (M42 #582):
 *
 * 1. Phase 1 — the pebble render in its fixed 120dp slot, snap or not.
 * 2. Phase 2 — once the ORIGINAL rendition is signed + decoded, the photo
 *    slides in above the pebble in the [BannerAspect] bucket nearest its
 *    intrinsic ratio, bottom-padded by half the slot so the pebble overlaps
 *    its lower edge; the slot gains a rounded system-background backdrop.
 *
 * Android's pebble render is static, so iOS's animation-finished gate is
 * trivially satisfied (its own static-pebble branch) — the reveal fires as
 * soon as the photo is ready. Any failure (null path, null cache in
 * previews, sign/download/decode) just leaves Phase 1 — no error UI. The
 * pebble slot stays structurally stable across the reveal, and the photo is
 * hidden from accessibility (iOS parity).
 */
@Composable
fun PebbleReadBanner(
    renderSvg: String?,
    valence: Valence,
    palette: EmotionPalette?,
    modifier: Modifier = Modifier,
    snapStoragePath: String? = null,
) {
    val system = PebblesTheme.colors.system
    val accentHex = PebblesTheme.colors.accent.primaryHex
    val strokeHex = palette?.pebbleFrameColors(valence.intensity)?.strokeHex ?: accentHex
    val heightDp =
        when (valence.sizeGroup) {
            ValenceSizeGroup.SMALL -> 80.dp
            ValenceSizeGroup.MEDIUM -> 100.dp
            ValenceSizeGroup.LARGE -> 116.dp
        }

    val context = LocalContext.current
    val snapUrls = LocalSnapURLCache.current
    var photo by remember(snapStoragePath) { mutableStateOf<BannerPhoto?>(null) }
    // Reveal timing honors the platform's remove-animations setting (the iOS
    // reduce-motion analog): shorter fade, no slide.
    val reduceMotion =
        remember(context) {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }

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

    val revealPhoto = photo != null
    Box(
        modifier = modifier.fillMaxWidth().heightIn(min = 120.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = revealPhoto,
            enter =
                if (reduceMotion) {
                    fadeIn(tween(durationMillis = 250))
                } else {
                    fadeIn(tween(durationMillis = 450)) +
                        slideInVertically(tween(durationMillis = 450)) { fullHeight -> -fullHeight / 3 }
                },
        ) {
            photo?.let { loaded ->
                Image(
                    bitmap = loaded.bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 60.dp)
                            .aspectRatio(loaded.aspect.ratio)
                            .clip(RoundedCornerShape(24.dp)),
                )
            }
        }
        // The pebble slot keeps composition identity across the reveal.
        Box(
            modifier =
                Modifier
                    .size(120.dp)
                    .then(
                        if (revealPhoto) {
                            Modifier.background(system.background, RoundedCornerShape(24.dp))
                        } else {
                            Modifier
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            if (renderSvg != null) {
                // PebbleStaticRender fit-scales + centers within the slot (the
                // iOS 120pt pebbleStableSlot), the pebble sized by valence.
                PebbleStaticRender(
                    svg = renderSvg,
                    strokeHex = strokeHex,
                    modifier = Modifier.fillMaxWidth().height(heightDp),
                )
            }
        }
    }
}
