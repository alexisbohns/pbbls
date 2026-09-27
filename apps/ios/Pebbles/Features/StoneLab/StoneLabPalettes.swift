#if DEBUG
import Foundation
import SwiftUI

/// One emotion group's six palette tones, as `#RRGGBBAA` hex. `dark` is the
/// darkest tone and `shaded` the mid one, as the `emotion_categories`
/// columns and the brand accent both have it.
struct StoneLabPalette: Identifiable, Hashable {
    let slug: String
    let primary: String
    let secondary: String
    let light: String
    let surface: String
    let dark: String
    let shaded: String

    var id: String { slug }

    typealias Slot = StonePalette.Slot

    func hex(_ slot: Slot) -> String {
        switch slot {
        case .primary: return primary
        case .secondary: return secondary
        case .light: return light
        case .surface: return surface
        case .dark: return dark
        case .shaded: return shaded
        }
    }

    func rgb(_ slot: Slot) -> SIMD3<Float> { StoneLabPalettes.rgb(hex(slot)) }
    func color(_ slot: Slot) -> Color { Color(hex: hex(slot)) ?? .clear }

    /// The same six tones as SwiftUI colours, for `StoneView`.
    var stonePalette: StonePalette {
        StonePalette(primary: color(.primary), secondary: color(.secondary), light: color(.light),
                     surface: color(.surface), dark: color(.dark), shaded: color(.shaded))
    }
}

/// The seven seeded palettes, hard-coded so the lab works offline and signed
/// out. `StoneLabPalettesTests` pins them to the migration literals.
enum StoneLabPalettes {
    static let all: [StoneLabPalette] = [
        StoneLabPalette(slug: "peace",   primary: "#487C5AFF", secondary: "#80BF96FF", light: "#EDF2EEFF", surface: "#487C5A1A", dark: "#0E1912FF", shaded: "#32573FFF"),
        StoneLabPalette(slug: "fear",    primary: "#7B5E99FF", secondary: "#AE91CCFF", light: "#F2EFF5FF", surface: "#7B5E991A", dark: "#19131FFF", shaded: "#56426BFF"),
        StoneLabPalette(slug: "joy",     primary: "#A15C08FF", secondary: "#CF8C39FF", light: "#FAF6EAFF", surface: "#A15C081A", dark: "#201202FF", shaded: "#714006FF"),
        StoneLabPalette(slug: "anger",   primary: "#8E4242FF", secondary: "#C17575FF", light: "#F4ECECFF", surface: "#8E42421A", dark: "#1C0D0DFF", shaded: "#632E2EFF"),
        StoneLabPalette(slug: "pride",   primary: "#A9478AFF", secondary: "#EA91CEFF", light: "#F6EDF3FF", surface: "#A9478A1A", dark: "#220E1CFF", shaded: "#763261FF"),
        StoneLabPalette(slug: "shame",   primary: "#868686FF", secondary: "#B9B9B9FF", light: "#F3F3F3FF", surface: "#8686861A", dark: "#1B1B1BFF", shaded: "#5E5E5EFF"),
        StoneLabPalette(slug: "sadness", primary: "#59658AFF", secondary: "#8C98BDFF", light: "#EEF0F3FF", surface: "#59658A1A", dark: "#12141CFF", shaded: "#3E4761FF"),
    ]

    /// `#RRGGBB` or `#RRGGBBAA` → 0..1 RGB for the shader: the gamma-encoded
    /// sRGB bytes divided by 255, not linearised. The shader lights these
    /// display-referred values directly. Alpha is ignored: opacity is a
    /// separate tone knob. Unparseable → black.
    static func rgb(_ hex: String) -> SIMD3<Float> {
        var digits = Substring(hex)
        if digits.hasPrefix("#") { digits = digits.dropFirst() }
        guard digits.count == 6 || digits.count == 8, let value = UInt32(digits.prefix(6), radix: 16) else {
            return SIMD3<Float>(repeating: 0)
        }
        return SIMD3<Float>(
            Float((value >> 16) & 0xFF) / 255,
            Float((value >> 8) & 0xFF) / 255,
            Float(value & 0xFF) / 255
        )
    }
}
#endif
