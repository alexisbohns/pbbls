import SwiftUI

/// The six tones a stone is coloured from. The picker builds one from the
/// brand accent, the Path from an emotion's palette, the lab from a hex
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

    /// The brand accent: `AccentDark` (#341B1B) is the darkest tone and
    /// `AccentShaded` (#8C4949) the mid one, the same convention as the
    /// `emotion_categories` columns.
    static let brand = StonePalette(
        primary: Color.accent.primary,
        secondary: Color.accent.secondary,
        light: Color.accent.light,
        surface: Color.accent.surface,
        dark: Color.accent.dark,
        shaded: Color.accent.shaded
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
