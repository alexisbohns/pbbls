import SwiftUI
import Testing
@testable import Pebbles

/// #1130 R5: which palette slot paints the card's edge and its spot ink, by
/// valence and scheme, and how strong the field's tint and pattern are.
@Suite("SpineColors")
struct SpineColorsTests {
    @Test("the edge: Paper high primary, neutral secondary, low dark; Night secondary, primary, shaded")
    func edge() {
        #expect(SpineColors.edgeSlot(.highlight, scheme: .light) == .primary)
        #expect(SpineColors.edgeSlot(.neutral, scheme: .light) == .secondary)
        #expect(SpineColors.edgeSlot(.lowlight, scheme: .light) == .dark)
        #expect(SpineColors.edgeSlot(.highlight, scheme: .dark) == .secondary)
        #expect(SpineColors.edgeSlot(.neutral, scheme: .dark) == .primary)
        #expect(SpineColors.edgeSlot(.lowlight, scheme: .dark) == .shaded)
    }

    @Test("the spot is primary on Paper and secondary at Night")
    func spot() {
        #expect(SpineColors.spotSlot(scheme: .light) == .primary)
        #expect(SpineColors.spotSlot(scheme: .dark) == .secondary)
    }

    @Test("the field tints at 9 % / 13 % and patterns at 34 % / 40 %")
    func strengths() {
        #expect(SpineColors.tintK(scheme: .light) == 0.09 && SpineColors.tintK(scheme: .dark) == 0.13)
        #expect(SpineColors.patternK(scheme: .light) == 0.34 && SpineColors.patternK(scheme: .dark) == 0.40)
    }

    @Test("a bare moment wears the blank ramp, a pebble with no emotion the brand one")
    func palettes() {
        #expect(SpineColors.palette(nil, isBlank: true) == .blank)
        #expect(SpineColors.palette(nil, isBlank: false) == .brand)
    }

    /// Android's SpineColors: rakeShadow = onSurface @ .20 Paper, black @ .34 Night;
    /// shadow = onSurface Paper, black Night, at shadowAlpha .30 / .62.
    @Test("the rake shadow and the shadow ink mirror Android, black being scrim")
    func shadows() {
        #expect(SpineColors.rakeShadowSource(scheme: .light) == (PebblesColorRole.onSurface, 0.2))
        #expect(SpineColors.rakeShadowSource(scheme: .dark) == (PebblesColorRole.scrim, 0.34))
        #expect(SpineColors.shadowInkRole(scheme: .light) == .onSurface)
        #expect(SpineColors.shadowInkRole(scheme: .dark) == .scrim)
        #expect(SpineColors.shadowAlpha(scheme: .light) == 0.30 && SpineColors.shadowAlpha(scheme: .dark) == 0.62)
    }
}
