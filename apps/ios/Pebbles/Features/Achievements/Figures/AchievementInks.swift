import SwiftUI

/// The boards' inks (#1306, NOTES "Ghosts", "Muted structure") on the app's
/// roles, so Paper and Night come from the colour scheme: `stock` is the card,
/// `ink` the text, `ink2` every "not yet", `rule` and `cardRule` the hairlines.
/// An emotion's hue is the Spine's (`SpineColors`): its family's spot, or its
/// deep ink under a heavy pebble. A family this build cannot resolve draws in
/// ink (spec §4), and so does a family the server left out. Android: `AchievementInks`.
struct AchievementInks {
    let stock = Color.pebbles.stock
    let ink = Color.pebbles.onSurface
    let ink2 = Color.pebbles.onSurfaceVariant
    let rule = Color.pebbles.rule
    let cardRule = Color.pebbles.cardRule
    /// The board's third ink (`--ink-3`, the disc's grain): Android's `ink3`, the scheme's outline.
    let ink3 = Color.pebbles.outline
    let scheme: ColorScheme

    init(scheme: ColorScheme) {
        self.scheme = scheme
    }

    /// A family's hue (the board's `famHue`): Paper `primary`, Night `secondary`; nil when unknown.
    func hue(_ palette: EmotionPalette?) -> Color? {
        palette.map { SpineColors.spot(StonePalette(emotion: $0), scheme: scheme) }
    }

    /// A pebble's seal ink (the board's `emoColor`): the family's deep ink under a heavy pebble.
    func sealInk(_ palette: EmotionPalette?, polarity: ValencePolarity) -> Color {
        guard let palette else { return ink }
        let stone = StonePalette(emotion: palette)
        return polarity == .lowlight ? SpineColors.deep(stone, scheme: scheme) : SpineColors.spot(stone, scheme: scheme)
    }

    /// A family's name ink (the board's `tx`): Night `secondary`; Paper `primary`, shame's `shaded`.
    func labelInk(_ palette: EmotionPalette?, family: String?) -> Color {
        guard let palette else { return ink }
        if scheme == .dark { return palette.secondary }
        return family == "shame" ? palette.shaded : palette.primary
    }

    /// The tint print's strength over the stock (`--tint-k`).
    var tintK: Float { scheme == .dark ? 0.18 : 0.15 }

    /// The wash print's strength over the stock (`--wash-k`).
    var washK: Float { scheme == .dark ? 0.12 : 0.09 }
}
