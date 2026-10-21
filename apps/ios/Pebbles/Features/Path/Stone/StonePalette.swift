import SwiftUI

/// The six tones a stone is coloured from. The picker builds one from
/// `brand`, the Path from an emotion's palette, the lab from a hex
/// sextet; a `StoneTones` value then says which slot each role (body, ink,
/// lips) takes. `dark` is the darkest tone and `shaded` the mid one.
struct StonePalette: Equatable {
    enum Slot: String, CaseIterable, Identifiable, Codable {
        case primary, secondary, light, surface, dark, shaded
        var id: String { rawValue }
    }

    let primary: Color
    let secondary: Color
    let light: Color
    let surface: Color
    let dark: Color
    let shaded: Color

    /// The brand stone (D5, spec 2026-10-20-ios-m3-colour): the #456 accent ramp
    /// kept verbatim as stone data, not scheme roles. The lit-stone shader (#975)
    /// is tuned to a body ramp, and a stone never changes with the theme. `dark`
    /// (#341B1B) is the darkest tone and `shaded` (#8C4949) the mid one, the
    /// `emotion_categories` convention.
    static let brand = StonePalette(
        primary: Color(.sRGB, red: 192 / 255, green: 122 / 255, blue: 122 / 255),
        secondary: Color(.sRGB, red: 234 / 255, green: 211 / 255, blue: 211 / 255),
        light: Color(.sRGB, red: 250 / 255, green: 244 / 255, blue: 244 / 255),
        surface: Color(.sRGB, red: 192 / 255, green: 122 / 255, blue: 122 / 255, opacity: 0.1),
        dark: Color(.sRGB, red: 52 / 255, green: 27 / 255, blue: 27 / 255),
        shaded: Color(.sRGB, red: 140 / 255, green: 73 / 255, blue: 73 / 255)
    )

    /// An uncompleted moment's stone (R4): the web `BLANK_PALETTE` grey ramp
    /// (`apps/web/lib/config/stone-tones.ts`), the same in both schemes.
    /// `surface` is the primary at 10% alpha, the rule the emotion palettes follow.
    static let blank = StonePalette(
        primary: Color(.sRGB, red: 134 / 255, green: 134 / 255, blue: 134 / 255),
        secondary: Color(.sRGB, red: 185 / 255, green: 185 / 255, blue: 185 / 255),
        light: Color(.sRGB, red: 243 / 255, green: 243 / 255, blue: 243 / 255),
        surface: Color(.sRGB, red: 134 / 255, green: 134 / 255, blue: 134 / 255, opacity: 0.1),
        dark: Color(.sRGB, red: 27 / 255, green: 27 / 255, blue: 27 / 255),
        shaded: Color(.sRGB, red: 94 / 255, green: 94 / 255, blue: 94 / 255)
    )

    /// An emotion's palette: same six slots, same convention (`dark` is the
    /// darkest tone, `shaded` the mid one), so the tables apply unchanged.
    init(emotion: EmotionPalette) {
        self.init(primary: emotion.primary, secondary: emotion.secondary, light: emotion.light,
                  surface: emotion.surface, dark: emotion.dark, shaded: emotion.shaded)
    }

    init(primary: Color, secondary: Color, light: Color, surface: Color, dark: Color, shaded: Color) {
        self.primary = primary
        self.secondary = secondary
        self.light = light
        self.surface = surface
        self.dark = dark
        self.shaded = shaded
    }

    func color(_ slot: Slot) -> Color {
        switch slot {
        case .primary: return primary
        case .secondary: return secondary
        case .light: return light
        case .surface: return surface
        case .dark: return dark
        case .shaded: return shaded
        }
    }

    /// Straight, display-referred sRGB for the shader, resolved for `scheme`
    /// because an asset colour differs between light and dark mode.
    func rgb(_ slot: Slot, scheme: ColorScheme) -> SIMD3<Float> {
        var environment = EnvironmentValues()
        environment.colorScheme = scheme
        let resolved = color(slot).resolve(in: environment)
        return SIMD3<Float>(resolved.red, resolved.green, resolved.blue)
    }
}
