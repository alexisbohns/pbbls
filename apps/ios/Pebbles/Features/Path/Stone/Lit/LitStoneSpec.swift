import SwiftUI

/// Everything one lit stone's image depends on (#1690): Android's `StoneSpec`
/// without its shadow tone and alpha, which the scheme decides and `night`
/// stands for here. A nil `palette` is a moment with no emotion yet: a blank
/// river pebble in the shame greys, uncarved, whatever its polarity. `flat` is
/// Low Power Mode: the body and the ink, nothing else.
///
/// Hashable because the image cache keys on it (Task 8).
struct LitStoneSpec: Hashable {
    let size: ValenceSizeGroup
    let polarity: ValencePolarity
    let palette: StonePalette?
    let glyph: PebbleGlyph?
    let seed: UInt32
    let night: Bool
    let flat: Bool
}

/// `StonePalette` is `Equatable` in its own file, where the compiler writes
/// `==` over its six colours. Swift will not synthesise `Hashable` from another
/// file, so the same six colours are hashed here, in the same order. Colours
/// are compared as the framework compares them, so two palettes that are `==`
/// hash alike.
extension StonePalette: Hashable {
    func hash(into hasher: inout Hasher) {
        hasher.combine(primary)
        hasher.combine(secondary)
        hasher.combine(light)
        hasher.combine(surface)
        hasher.combine(dark)
        hasher.combine(shaded)
    }
}
