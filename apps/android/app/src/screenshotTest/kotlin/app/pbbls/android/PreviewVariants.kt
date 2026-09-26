package app.pbbls.android

import androidx.compose.ui.tooling.preview.Preview

/*
 * Shared preview variants for the screenshot-validation gate (#847).
 *
 * The suite's baseline is one device at `fontScale = 1.0` in `en`, which is
 * exactly the configuration that never renders the two failure modes the app
 * actually ships: a fixed-height control clipping its label at an accessibility
 * font scale, and a French string overflowing a row sized for its English
 * sibling. These multipreview annotations add those two axes to a preview
 * without duplicating its body — stacking one on an existing `@PreviewTest`
 * function emits an extra reference PNG and leaves the original's file name
 * untouched, so adding a variant never re-baselines what was already there.
 *
 * Only the extremes are rendered. Every variant doubles the committed baseline,
 * and 1.5x has never been the scale that breaks a layout 2.0x leaves intact —
 * the intermediate scales in `androidx`'s own `@PreviewFontScale` would cost
 * five more PNGs per preview to tell us nothing new.
 *
 * `…Tall` carries `heightDp` for the previews that pin a viewport height: those
 * previews exist to show a screen inside a phone-sized window, and a variant
 * that dropped the height would wrap to its content and hide the very clipping
 * it was added to catch.
 */

/** Largest accessibility font scale, wrap-content height. */
@Preview(name = "fs2", showBackground = true, fontScale = 2f)
annotation class PreviewLargeFont

/** Largest accessibility font scale inside a phone-height viewport. */
@Preview(name = "fs2", showBackground = true, fontScale = 2f, heightDp = 720)
annotation class PreviewLargeFontTall

/** French (`values-fr`), wrap-content height. */
@Preview(name = "fr", showBackground = true, locale = "fr")
annotation class PreviewFrench

/*
 * Large-screen widths (#855): 840 dp is the Medium/Expanded breakpoint (an
 * unfolded foldable or a portrait tablet), 1024 dp a landscape tablet or a
 * desktop window. Both sit above the 600 dp readable-column cap, so these
 * renders are what show the cap holding: centered content, nothing stretched.
 */

/** Medium/Expanded widths, wrap-content height. */
@Preview(name = "w840", showBackground = true, widthDp = 840)
@Preview(name = "w1024", showBackground = true, widthDp = 1024)
annotation class PreviewWide

/** Medium/Expanded widths inside a tablet-height viewport. */
@Preview(name = "w840", showBackground = true, widthDp = 840, heightDp = 720)
@Preview(name = "w1024", showBackground = true, widthDp = 1024, heightDp = 720)
annotation class PreviewWideTall
