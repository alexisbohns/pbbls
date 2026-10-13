import Foundation

/// What a stone has carved into it.
enum StoneCarving: Hashable {
    /// The bundled sample glyph over the seed's veins: pickers and the lab.
    case sample
    /// A real pebble: the seed's veins and fossil, plus its derived glyph
    /// when it has one (spec D5). Nil draws no glyph, never the sample.
    case pebble(PebbleGlyph?)
    /// An uncompleted moment (R4): the engine outline only, nothing carved
    /// inside (no glyph, veins or fossil).
    case blank
}
