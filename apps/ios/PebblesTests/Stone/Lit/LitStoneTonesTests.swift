import SwiftUI
import Testing
@testable import Pebbles

/// `stone-tones.ts` transcribed over the pride palette
/// (`apps/web/lib/seed/sandbox-palettes.ts`), through the Android port's test,
/// plus the two colour mixes and the night filter the web leaves to the browser.
/// Colours compare as 0-255 sRGB channels within one step.
@Suite("Lit stone tones")
struct LitStoneTonesTests {
    private typealias Tones = LitStoneTones

    private let pride: EmotionPalette = {
        guard let palette = EmotionPalette(
            primaryHex: "#A9478AFF", secondaryHex: "#EA91CEFF", lightHex: "#F6EDF3FF",
            surfaceHex: "#A9478A1A", darkHex: "#220E1CFF", shadedHex: "#763261FF"
        ) else { fatalError("pride fixture failed to parse") }
        return palette
    }()
    private var p: StonePalette { StonePalette(emotion: pride) }

    private func rgb(_ color: Color) -> [Double] {
        var environment = EnvironmentValues()
        environment.colorScheme = .light
        let r = color.resolve(in: environment)
        return [Double(r.red) * 255, Double(r.green) * 255, Double(r.blue) * 255]
    }

    private func same(_ a: Color, _ b: Color, _ tolerance: Double = 1) -> Bool {
        zip(rgb(a), rgb(b)).allSatisfy { abs($0 - $1) <= tolerance }
    }

    private func hex(_ color: Color) -> String {
        let c = rgb(color).map { Int($0.rounded()) }
        return String(format: "#%02X%02X%02X", c[0], c[1], c[2])
    }

    private func same(_ a: LitPaint, _ b: LitPaint) -> Bool {
        same(a.color, b.color) && a.opacity == b.opacity && a.width == b.width
            && a.offset == b.offset && a.glow == b.glow
    }

    private func same(_ a: [LitPaint], _ b: [LitPaint]) -> Bool {
        a.count == b.count && zip(a, b).allSatisfy { same($0, $1) }
    }

    /// Row-major 4x5 `out = M x [r g b a 1]`, channels 0...1, clamped.
    private func apply(_ m: [Double], to color: [Double]) -> [Double] {
        let input = color + [1, 1]
        return (0..<4).map { row in
            min(max((0..<5).reduce(0) { $0 + m[row * 5 + $1] * input[$1] }, 0), 1)
        }
    }

    @Test("the stone palette maps the five slots")
    func paletteMapsSlots() {
        #expect(p.primary == pride.primary)
        #expect(p.secondary == pride.secondary)
        #expect(p.light == pride.light)
        #expect(p.shaded == pride.shaded)
        #expect(p.dark == pride.dark)
        #expect(hex(p.primary) == "#A9478A")
        #expect(hex(p.dark) == "#220E1C")
    }

    @Test("the blank palette is the web BLANK_PALETTE, hex for hex")
    func blankPaletteIsTheShameGreys() {
        let blank = Tones.blankPalette
        #expect(hex(blank.primary) == "#868686")
        #expect(hex(blank.secondary) == "#B9B9B9")
        #expect(hex(blank.light) == "#F3F3F3")
        #expect(hex(blank.shaded) == "#5E5E5E")
        #expect(hex(blank.dark) == "#1B1B1B")
    }

    @Test("StonePalette.blank is left as it was")
    func existingBlankUntouched() {
        let blank = StonePalette.blank
        #expect(hex(blank.primary) == "#868686")
        #expect(hex(blank.secondary) == "#B9B9B9")
        #expect(hex(blank.light) == "#F3F3F3")
        #expect(hex(blank.shaded) == "#5E5E5E")
        #expect(hex(blank.dark) == "#1B1B1B")
        #expect(abs(Double(blank.surface.resolve(in: EnvironmentValues()).opacity) - 0.1) < 0.01)
    }

    @Test("highlight body ramp")
    func highlightRamp() {
        let ramp = Tones.bodyRamp(.highlight, p)
        #expect(ramp.map(\.offset) == [0, 0.4, 1])
        #expect(same(ramp[0].color, Tones.mixSrgb(p.secondary, 0.66, p.light)))
        #expect(same(ramp[1].color, p.primary))
        #expect(same(ramp[2].color, Tones.mixSrgb(p.shaded, 0.7, p.dark)))
        // Reference values of the same mixes over pride.
        #expect(hex(ramp[0].color) == "#EEB0DB")
        #expect(hex(ramp[2].color) == "#5D274C")
    }

    @Test("neutral body ramp")
    func neutralRamp() {
        let ramp = Tones.bodyRamp(.neutral, p)
        #expect(ramp.map(\.offset) == [0, 0.5, 1])
        #expect(same(ramp[0].color, Tones.mixSrgb(p.secondary, 0.72, p.light)))
        #expect(same(ramp[1].color, p.secondary))
        #expect(same(ramp[2].color, Tones.mixSrgb(p.primary, 0.72, p.secondary)))
        #expect(hex(ramp[0].color) == "#EDABD8")
        #expect(hex(ramp[2].color) == "#BB5C9D")
    }

    @Test("lowlight body ramp ends on dark")
    func lowlightRamp() {
        let ramp = Tones.bodyRamp(.lowlight, p)
        #expect(ramp.map(\.offset) == [0, 0.5, 1])
        #expect(same(ramp[0].color, Tones.mixOklab(p.secondary, 0.31, p.shaded)))
        #expect(same(ramp[1].color, Tones.mixOklab(p.shaded, 0.53, p.dark)))
        #expect(same(ramp[2].color, p.dark))
        #expect(hex(ramp[0].color) == "#994E81")
        #expect(hex(ramp[1].color) == "#4D203F")
    }

    @Test("glyph tones")
    func glyph() {
        let (lip, ink) = Tones.glyphTones(.lowlight, p)
        #expect(same(lip, LitPaint(p.secondary, 0.5)))
        #expect(same(ink.color, Color(.sRGB, red: 0, green: 0, blue: 0)))
        #expect(ink.opacity == 0.66)
        #expect(same(Tones.glyphTones(.highlight, p).lip, LitPaint(p.light, 0.6)))
        #expect(same(Tones.glyphTones(.neutral, p).ink, LitPaint(p.shaded, 0.9)))
        let gemInk = Tones.glyphTones(.highlight, p).ink
        #expect(hex(gemInk.color) == "#502242")
        #expect(gemInk.opacity == 0.85)
    }

    @Test("vein tones")
    func veins() {
        let neutral = Tones.veinTones(.neutral, p)
        #expect(neutral.count == 2)
        #expect(neutral.map(\.width) == [9, 2.4])
        #expect(neutral[1].offset == CGVector(dx: 1.5, dy: 3.5))
        #expect(neutral[0].offset == .zero)
        let lowlight = Tones.veinTones(.lowlight, p)
        #expect(lowlight[0].glow)
        #expect(!lowlight[1].glow)
        #expect(same(lowlight[1].color, Tones.mixSrgb(p.primary, 0.5, p.secondary)))
        // 201.5 on the red channel: either rounding is within a step.
        #expect(same(lowlight[1].color, Color(.sRGB, red: 202 / 255, green: 108 / 255, blue: 172 / 255)))
        #expect(same(Tones.veinTones(.highlight, p), [LitPaint(p.light, 0.3, width: 3)]))
    }

    @Test("fossil tones")
    func fossils() {
        #expect(same(Tones.fossilTones(.highlight, p), [LitPaint(p.light, 0.18)]))
        let neutral = Tones.fossilTones(.neutral, p)
        #expect(same(neutral[0], LitPaint(p.light, 0.35, offset: CGVector(dx: -1.5, dy: -2))))
        #expect(neutral[0].width == nil)
        #expect(same(neutral[1], LitPaint(p.primary, 0.3)))
        #expect(same(Tones.fossilTones(.lowlight, p), [LitPaint(p.dark, 0.5)]))
    }

    @Test("edge shade is dark at three widths")
    func edge() {
        #expect(same(Tones.edgeShade(.highlight, p), LitPaint(p.dark, 0.5, width: 5)))
        #expect(same(Tones.edgeShade(.neutral, p), LitPaint(p.dark, 0.26, width: 6)))
        #expect(same(Tones.edgeShade(.lowlight, p), LitPaint(p.dark, 0.8, width: 6)))
    }

    @Test("glint and grain strengths")
    func strengths() {
        #expect(Tones.glintStrength(.highlight) == 1)
        #expect(Tones.glintStrength(.neutral) == 0.3)
        #expect(Tones.glintStrength(.lowlight) == 0.34)
        #expect(Tones.grainStrength(.highlight) == 0)
        #expect(Tones.grainStrength(.neutral) == 0.14)
        #expect(Tones.grainStrength(.lowlight) == 0.32)
    }

    @Test("slot colours")
    func slots() {
        #expect(same(Tones.slotColor(.primary, p), p.primary))
        #expect(same(Tones.slotColor(.secondary, p), p.secondary))
        #expect(same(Tones.slotColor(.light, p), p.light))
        #expect(same(Tones.slotColor(.shaded, p), p.shaded))
        #expect(same(Tones.slotColor(.dark, p), p.dark))
        #expect(hex(Tones.slotColor(.white, p)) == "#FFFFFF")
    }

    @Test("mixSrgb is a per-channel lerp of the encoded values")
    func srgbMix() {
        let white = Color(.sRGB, red: 1, green: 1, blue: 1)
        let black = Color(.sRGB, red: 0, green: 0, blue: 0)
        #expect(hex(Tones.mixSrgb(white, 0.5, black)) == "#808080")
        #expect(same(Tones.mixSrgb(p.secondary, 1, p.light), p.secondary))
        #expect(same(Tones.mixSrgb(p.secondary, 0, p.light), p.light))
    }

    @Test("mixOklab midpoint of white and black is OKLab L one half")
    func oklabMidpoint() {
        // L = .5 is linear .125, which encodes to sRGB ~.389 (#636363): darker
        // than the sRGB midpoint, as the browser's color-mix(in oklab) draws it.
        let white = Color(.sRGB, red: 1, green: 1, blue: 1)
        let black = Color(.sRGB, red: 0, green: 0, blue: 0)
        let mid = rgb(Tones.mixOklab(white, 0.5, black))
        for channel in mid { #expect(abs(channel - 0.389 * 255) < 0.01 * 255) }
        #expect(mid[0] < 128)
        #expect(hex(Tones.mixOklab(white, 0.5, black)) == "#636363")
    }

    @Test("mixOklab endpoints are exact")
    func oklabEndpoints() {
        #expect(Tones.mixOklab(p.secondary, 1, p.shaded) == p.secondary)
        #expect(Tones.mixOklab(p.secondary, 0, p.shaded) == p.shaded)
    }

    @Test("mixOklab round-trips a colour through OKLab within a step")
    func oklabRoundTrip() {
        // A 50 % mix of a colour with itself exercises both conversions.
        #expect(same(Tones.mixOklab(p.primary, 0.5, p.primary), p.primary))
    }

    @Test("night adjust pairs")
    func nightPairs() {
        #expect(Tones.nightAdjust(.highlight) == (0.92, 0.96))
        #expect(Tones.nightAdjust(.neutral) == (0.92, 0.96))
        #expect(Tones.nightAdjust(.lowlight) == (1.2, 1.05))
    }

    @Test("night matrix dims and desaturates the stone")
    func nightMatrixStone() {
        let red = apply(Tones.nightMatrix(.neutral), to: [1, 0, 0, 1])
        #expect((0.85...0.95).contains(red[0]), "red \(red[0])")
        #expect(red[1] > 0 && red[1] < 0.05, "green \(red[1])")
        #expect(red[2] > 0 && red[2] < 0.05, "blue \(red[2])")
        #expect(red[3] == 1)
        // The gem and the river pebble share the stone filter; only lava differs.
        #expect(apply(Tones.nightMatrix(.highlight), to: [1, 0, 0, 1]) == red)
    }

    @Test("night matrix brightens lava")
    func nightMatrixLava() {
        let grey = apply(Tones.nightMatrix(.lowlight), to: [0.5, 0.5, 0.5, 1])
        #expect(abs(grey[0] - 0.6) < 0.01)
        #expect(abs(grey[0] - grey[1]) < 1e-4)
        #expect(abs(grey[0] - grey[2]) < 1e-4)
    }
}
