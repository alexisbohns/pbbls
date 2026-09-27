import SwiftUI

/// The read-view "Petroglyph" (issue #599), since #974 the lit stone: the
/// pebble's own glyph carved into stone coloured by its emotion, the light
/// following the phone in the hand and sweeping once on appearance. Shown
/// either as the page heading or overlapping the snap's top-right corner
/// (`PebbleSnapFrame`); sized by the caller, it fits its outline aspect
/// inside whatever frame it gets.
///
/// `renderVersion` is kept for the call sites: the stone render does not
/// depend on it, and the read banner still uses the version's timings to
/// gate its photo reveal.
struct PebbleReadPetroglyph: View {
    let renderSvg: String?
    let renderVersion: String?
    let valence: Valence
    let palette: EmotionPalette?

    var body: some View {
        if renderSvg != nil {
            PebbleStoneView(valence: valence, renderSvg: renderSvg, palette: palette, followsTilt: true)
        }
    }
}
