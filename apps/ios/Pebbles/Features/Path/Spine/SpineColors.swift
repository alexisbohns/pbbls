import SwiftUI

/// The Spine's blends with a pebble's emotion palette (#1130 R5, R17). The
/// palette is runtime data (`v_emotions_with_palette`), so these are computed
/// here and are never colorsets (stack A §2.4). The scheme-only tones are
/// `Color.pebbles.*`, except the rake shadow and the shadow ink, which Android
/// also blends from the scheme in `SpineColors.kt` (decision 1). The slot and
/// role choices are pure and tested; the colour is the palette's or the role's.
enum SpineColors {
    /// The stone palette a moment wears: blank for a bare moment, the brand
    /// ramp for a pebble with no known emotion.
    static func palette(_ emotion: EmotionPalette?, isBlank: Bool) -> StonePalette {
        if isBlank { return .blank }
        return emotion.map(StonePalette.init(emotion:)) ?? .brand
    }

    /// The card's painted edge: Paper high `primary`, neutral `secondary`, low
    /// `dark`; Night high `secondary`, neutral `primary`, low `shaded`.
    static func edgeSlot(_ polarity: ValencePolarity, scheme: ColorScheme) -> StonePalette.Slot {
        let night = scheme == .dark
        switch polarity {
        case .highlight: return night ? .secondary : .primary
        case .neutral: return night ? .primary : .secondary
        case .lowlight: return night ? .shaded : .dark
        }
    }

    /// The spot ink: the field's tint and pattern, the rail's emotion. Paper `primary`, Night `secondary`.
    static func spotSlot(scheme: ColorScheme) -> StonePalette.Slot {
        scheme == .dark ? .secondary : .primary
    }

    /// The spot over the stock: `color-mix(spot 9 %, stock)`, 13 % at Night.
    static func tintK(scheme: ColorScheme) -> Double { scheme == .dark ? 0.13 : 0.09 }

    /// The valence pattern's strength over the field.
    static func patternK(scheme: ColorScheme) -> Double { scheme == .dark ? 0.40 : 0.34 }

    static func edge(_ palette: StonePalette, polarity: ValencePolarity, scheme: ColorScheme) -> Color {
        palette.color(edgeSlot(polarity, scheme: scheme))
    }

    static func spot(_ palette: StonePalette, scheme: ColorScheme) -> Color {
        palette.color(spotSlot(scheme: scheme))
    }

    // MARK: - Scheme blends Android computes in `SpineColors.kt` (decision 1)

    /// The rake's groove shadow: `onSurface` @ 0.20 on Paper, black @ 0.34 at
    /// Night. Black is `scrim`, which M3 keeps `#000000` in every scheme
    /// (`Features/` may not name `.black`). The lit line is the `rakeLight` token.
    static func rakeShadowSource(scheme: ColorScheme) -> (role: PebblesColorRole, alpha: Double) {
        scheme == .dark ? (.scrim, 0.34) : (.onSurface, 0.2)
    }

    static func rakeShadow(scheme: ColorScheme) -> Color {
        let source = rakeShadowSource(scheme: scheme)
        return role(source.role).opacity(source.alpha)
    }

    /// The shadow ink under the card: `onSurface` on Paper, black at Night,
    /// carried with Android's `shadowAlpha`.
    static func shadowInkRole(scheme: ColorScheme) -> PebblesColorRole {
        scheme == .dark ? .scrim : .onSurface
    }

    static func shadowInk(scheme: ColorScheme) -> Color {
        role(shadowInkRole(scheme: scheme))
    }

    /// Android's `shadowAlpha`: 0.30 on Paper, 0.62 at Night.
    static func shadowAlpha(scheme: ColorScheme) -> Double {
        scheme == .dark ? 0.62 : 0.30
    }

    /// The two roles the blends use, named through `Color.pebbles`.
    private static func role(_ role: PebblesColorRole) -> Color {
        role == .scrim ? Color.pebbles.scrim : Color.pebbles.onSurface
    }
}
