import SwiftUI

/// Emotion-palette colors for the whole pebble read page (issue #605), resolved
/// per element AND color scheme. Before #605 only the Petroglyph carried the
/// palette and the rest of the page used the system/accent chrome; now the page
/// tints to the pebble's emotion palette per this table:
///
/// | element         | light     | dark    |
/// | --------------- | --------- | ------- |
/// | background      | light     | dark    |
/// | name (title)    | shaded    | light   |
/// | date            | secondary | primary |
/// | tile.background | surface   | surface |
/// | tile.icon       | secondary | primary |
/// | tile.label      | shaded    | light   |
/// | description     | shaded    | light   |
/// | soul.glyph      | secondary | primary |
/// | soul.name       | primary   | light   |
///
/// Pure value type — resolved once at the read-view root and threaded into the
/// leaf composables as `Color` parameters, keeping them palette-agnostic.
/// Callers fall back to the system/accent chrome when the palette is
/// unavailable (cache miss). Mirrors the #599 `PetroglyphColors` resolver.
struct PebblePageColors: Equatable {
    let background: Color
    let title: Color
    let date: Color
    let tileBackground: Color
    let tileIcon: Color
    let tileLabel: Color
    let description: Color
    let soulGlyph: Color
    let soulName: Color
}

extension EmotionPalette {
    /// Read-page colors (issue #605) for the given color scheme — see
    /// `PebblePageColors` for the full role table.
    func pebblePageColors(for scheme: ColorScheme) -> PebblePageColors {
        if scheme == .dark {
            return PebblePageColors(
                background: dark,
                title: light,
                date: primary,
                tileBackground: surface,
                tileIcon: primary,
                tileLabel: light,
                description: light,
                soulGlyph: primary,
                soulName: light
            )
        } else {
            return PebblePageColors(
                background: light,
                title: shaded,
                date: secondary,
                tileBackground: surface,
                tileIcon: secondary,
                tileLabel: shaded,
                description: shaded,
                soulGlyph: secondary,
                soulName: primary
            )
        }
    }
}
