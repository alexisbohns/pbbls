import SwiftUI

/// A real pebble as lit stone (#1690), fitted into whatever frame it is given:
/// the seed's veins and fossil for its valence, its own glyph carved from its
/// derived glyph's strokes, coloured by its emotion's palette (the brand
/// accent when the palette is unknown). An uncompleted moment draws as the
/// blank stone (`blank(seed:)`, R4). It is the cached vector stone
/// (`LitStoneImage`), the one web and Android paint: a real pebble passes
/// `StableHash.seed(pebble.id)`, so it is the same stone on every surface and
/// every screen (D4).
///
/// Nothing redraws by itself: a still stone is a still image, and Low Power
/// Mode draws the flat rendering.
struct PebbleStoneView: View {
    let valence: Valence
    /// Nil is the blank stone: no glyph, the shame greys.
    let stonePalette: StonePalette?
    let glyph: PebbleGlyph?
    /// `StableHash.seed(pebble.id)`, the full 32 bits: the same stone as web and Android (D4).
    let seed: UInt32

    /// A completed pebble: its derived glyph, coloured by its emotion
    /// (the brand accent when the palette is unknown).
    init(valence: Valence, glyph: PebbleGlyph?, palette: EmotionPalette?, seed: UInt32) {
        self.valence = valence
        self.glyph = glyph
        self.stonePalette = palette.map(StonePalette.init(emotion:)) ?? .brand
        self.seed = seed
    }

    /// An uncompleted moment (R4): neutral-medium silhouette, outline only,
    /// the fixed grey ramp, labelled "Not a pebble yet" for VoiceOver.
    static func blank(seed: UInt32) -> PebbleStoneView {
        PebbleStoneView(valence: .neutralMedium, blankSeed: seed)
    }

    private init(valence: Valence, blankSeed: UInt32) {
        self.valence = valence
        self.glyph = nil
        self.stonePalette = nil
        self.seed = blankSeed
    }

    private var isBlank: Bool { stonePalette == nil }

    var body: some View {
        // `LitStoneImage` reads the scheme and Low Power Mode itself; the spec's own are not used.
        LitStoneImage(spec: LitStoneSpec(size: valence.sizeGroup, polarity: valence.polarity,
                                         palette: stonePalette, glyph: glyph, seed: seed,
                                         night: false, flat: false))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(isBlank ? Text("Not a pebble yet") : Text(verbatim: ""))
            .accessibilityHidden(!isBlank)
            .accessibilityAddTraits(isBlank ? .isImage : [])
    }
}
