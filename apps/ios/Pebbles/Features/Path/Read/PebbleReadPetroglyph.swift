import SwiftUI

/// The read-view "Petroglyph" (#599), the lit stone: the pebble's derived
/// glyph carved into stone coloured by its emotion, or the blank stone for an
/// uncompleted moment, the light following the phone and sweeping once.
/// Shown either as the page heading or overlapping the snap's top-right
/// corner (`PebbleSnapFrame`); sized by the caller, it fits its outline
/// aspect inside whatever frame it gets.
struct PebbleReadPetroglyph: View {
    /// Nil for an uncompleted moment.
    let pebble: MomentDetail.Pebble?
    let palette: EmotionPalette?

    var body: some View {
        if let pebble {
            PebbleStoneView(valence: pebble.valence, glyph: pebble.glyph, palette: palette, followsTilt: true)
        } else {
            PebbleStoneView.blank(followsTilt: true)
        }
    }
}
