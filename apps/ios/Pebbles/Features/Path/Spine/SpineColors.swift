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

    /// The painted edge, from the emotion's own palette. A moment with no emotion
    /// paints none, as Android's `SpineCardShell` ("the web's --edge is unset").
    static func edge(_ emotion: EmotionPalette?, polarity: ValencePolarity, scheme: ColorScheme) -> Color? {
        emotion.map { StonePalette(emotion: $0).color(edgeSlot(polarity, scheme: scheme)) }
    }

    static func spot(_ palette: StonePalette, scheme: ColorScheme) -> Color {
        palette.color(spotSlot(scheme: scheme))
    }

    /// The seal's deep ink (R17): `shaded` on Paper; at Night `secondary` mixed
    /// 80 % toward the ground, drawn as `secondary` at 0.8 over the page's
    /// `surface` (iOS 17 has no colour mix; the seal always sits on the ground).
    static func deepSlot(scheme: ColorScheme) -> (slot: StonePalette.Slot, opacity: Double) {
        scheme == .dark ? (.secondary, 0.8) : (.shaded, 1)
    }

    static func deep(_ palette: StonePalette, scheme: ColorScheme) -> Color {
        let deep = deepSlot(scheme: scheme)
        return palette.color(deep.slot).opacity(deep.opacity)
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

    /// The shadow ink under the card (and under a stone): `onSurface` on Paper,
    /// black at Night. The card's layers carry their own alphas (`cardShadow`).
    static func shadowInkRole(scheme: ColorScheme) -> PebblesColorRole {
        scheme == .dark ? .scrim : .onSurface
    }

    static func shadowInk(scheme: ColorScheme) -> Color {
        role(shadowInkRole(scheme: scheme))
    }

    /// Android's `SpineColors.shadowAlpha`: 0.30 on Paper, 0.62 at Night. Android
    /// gives it to the stone's drop shadow (`LitStone`), not to the card.
    static func shadowAlpha(scheme: ColorScheme) -> Double {
        scheme == .dark ? 0.62 : 0.30
    }

    /// One layer of the card's shadow, the web's `--spine-card-shadow`
    /// (`0 dy blur spread rgba(shadow, alpha)`), in points.
    struct CardShadowLayer: Equatable {
        let dy: CGFloat
        let blur: CGFloat
        let spread: CGFloat
        let alpha: Double
    }

    /// Android's `PAPER_SHADOW` / `NIGHT_SHADOW` (`features/path/spine/SpineCard.kt`):
    /// three layers on Paper, two at Night, each in `shadowInk` at its own alpha.
    static func cardShadow(scheme: ColorScheme) -> [CardShadowLayer] {
        scheme == .dark
            ? [CardShadowLayer(dy: 1, blur: 2, spread: 0, alpha: 0.5),
               CardShadowLayer(dy: 18, blur: 30, spread: -18, alpha: 0.9)]
            : [CardShadowLayer(dy: 0.5, blur: 0, spread: 0, alpha: 0.10),
               CardShadowLayer(dy: 1, blur: 2, spread: 0, alpha: 0.07),
               CardShadowLayer(dy: 18, blur: 30, spread: -22, alpha: 0.55)]
    }

    /// Android's `drawNightRing` width: a hairline just outside the card, at Night only.
    static let nightRingWidth: CGFloat = 0.5

    /// The Night ring: the web's `rgba(255,245,225,.06)`, which Android writes as
    /// white @ 0.06. `Features/` names no hex, so it is the warm `onSurface` @ 0.06
    /// (`#E7E2D9` at Night, white in high contrast). Paper has none.
    static func nightRingSource(scheme: ColorScheme) -> (role: PebblesColorRole, alpha: Double)? {
        scheme == .dark ? (.onSurface, 0.06) : nil
    }

    static func nightRing(scheme: ColorScheme) -> Color? {
        nightRingSource(scheme: scheme).map { role($0.role).opacity($0.alpha) }
    }

    /// The two roles the blends use, named through `Color.pebbles`.
    private static func role(_ role: PebblesColorRole) -> Color {
        role == .scrim ? Color.pebbles.scrim : Color.pebbles.onSurface
    }
}
