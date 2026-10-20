package app.pbbls.android.testing

/**
 * The SDK every pixel-reading Robolectric test runs on (#1141): a `@GraphicsMode(NATIVE)` class
 * that calls `captureToImage()` carries `@Config(sdk = [PIXEL_TEST_SDK])`.
 *
 * On SDK 37 Robolectric draws windows for real (it cannot be turned off there), and its PixelCopy
 * hands back the last frame the window's surface received instead of drawing the view again;
 * Compose's `captureToImage` skips its forced redraw under Robolectric, trusting PixelCopy to.
 * After some earlier tests in the same JVM, the frame that carries the content never reaches the
 * surface (the native render thread outlives each test), so the capture reads the window from
 * before `setContent`: the bare window background, whatever the composable draws. Whether it
 * does hangs on which tests ran first and on timing, so it fails on some full runs only.
 *
 * On 36 PixelCopy renders the view's current display list into a fresh surface, synchronously,
 * on every capture: what the test composed is what it reads, in any order.
 */
const val PIXEL_TEST_SDK = 36
