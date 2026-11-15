package app.pbbls.android.core.data

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Ported from iOS `ImagePipelineCroppedTests` (#1659): a cropped bitmap through the caps, the ladder and a metadata-free encode. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImagePipelineCroppedTest {
    private fun solid(
        width: Int,
        height: Int,
    ): Bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(77, 153, 51)) }

    private fun pixelSize(jpeg: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
        return options.outWidth to options.outHeight
    }

    /** Every JPEG marker before the scan, by its second byte (0xE1 is APP1, where EXIF and XMP live). */
    private fun markersBeforeScan(jpeg: ByteArray): List<Int> {
        val markers = mutableListOf<Int>()
        var i = 2 // past SOI
        while (i + 3 < jpeg.size) {
            if (jpeg[i].toInt() and 0xFF != 0xFF) break
            val marker = jpeg[i + 1].toInt() and 0xFF
            markers += marker
            if (marker == 0xDA) break // SOS: the image data follows
            val length = ((jpeg[i + 2].toInt() and 0xFF) shl 8) or (jpeg[i + 3].toInt() and 0xFF)
            i += 2 + length
        }
        return markers
    }

    @Test
    fun `a cropped image comes out as a 1024 original and a 420 thumb, ratio kept`() {
        val processed = ImagePipeline.process(solid(1600, 2000))
        assertEquals(819 to 1024, pixelSize(processed.original))
        assertEquals(336 to 420, pixelSize(processed.thumb))
    }

    @Test
    fun `a small crop is not upscaled`() {
        val processed = ImagePipeline.process(solid(600, 600))
        assertEquals(600 to 600, pixelSize(processed.original))
        assertEquals(420 to 420, pixelSize(processed.thumb))
    }

    @Test
    fun `no EXIF or XMP segment is written`() {
        val processed = ImagePipeline.process(solid(800, 1000))
        for (jpeg in listOf(processed.original, processed.thumb)) {
            val markers = markersBeforeScan(jpeg)
            assertTrue("no scan found: $markers", markers.contains(0xDA))
            assertFalse("APP1 present: $markers", markers.contains(0xE1))
        }
    }

    @Test
    fun `the source bitmap is left for the caller`() {
        val source = solid(1600, 2000)
        ImagePipeline.process(source)
        assertFalse(source.isRecycled)
    }

    /** Deterministic noise (splitmix64), so the byte-cap test never flakes. */
    private fun noise(
        width: Int,
        height: Int,
        amplitude: Int,
    ): Bitmap {
        var state = 0x9E37_79B9_7F4A_7C15uL

        fun next(): ULong {
            state += 0x9E37_79B9_7F4A_7C15uL
            var z = state
            z = (z xor (z shr 30)) * 0xBF58_476D_1CE4_E5B9uL
            z = (z xor (z shr 27)) * 0x94D0_49BB_1331_11EBuL
            return z xor (z shr 31)
        }
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val base = 128 + (i % width) * 40 / width

            fun channel() = (base + (next() % (amplitude * 2 + 1).toULong()).toInt() - amplitude).coerceIn(0, 255)
            pixels[i] = Color.rgb(channel(), channel(), channel())
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    @Test
    fun `a noisy 1024 px crop still fits the 1 MB and 300 KB caps`() {
        for (amplitude in listOf(40, 127)) {
            val processed = ImagePipeline.process(noise(1024, 1024, amplitude))
            assertTrue("original ${processed.original.size}", processed.original.size <= SnapBudgets.ORIGINAL_MAX_BYTES)
            assertTrue("thumb ${processed.thumb.size}", processed.thumb.size <= SnapBudgets.THUMB_MAX_BYTES)
        }
    }

    @Test
    fun `the working image is decoded upright and capped at 2048`() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val big =
            File(
                context.cacheDir,
                "big.png",
            ).apply { outputStream().use { solid(3000, 4000).compress(Bitmap.CompressFormat.PNG, 100, it) } }
        val image = ImagePipeline.decodeUpright(context, Uri.fromFile(big), CropSource.WORKING_MAX_EDGE_PX)
        assertEquals(1536 to 2048, image.width to image.height)
        val small =
            File(context.cacheDir, "small.png").apply {
                outputStream().use { solid(600, 800).compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        val kept = ImagePipeline.decodeUpright(context, Uri.fromFile(small), CropSource.WORKING_MAX_EDGE_PX)
        assertEquals(600 to 800, kept.width to kept.height)
    }
}
