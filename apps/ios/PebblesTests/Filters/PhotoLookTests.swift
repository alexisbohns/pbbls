import Testing
@testable import Pebbles

@Suite("PhotoLook")
struct PhotoLookTests {
    private static func withExtras(_ params: FilterParams) -> FilterParams {
        var out = params
        out.vignette = 0.1
        out.blurVignette = 0.2
        out.grain = 0.4
        return out
    }

    @Test("Adjust covers the ten tone and colour controls plus refocus, never the extras")
    func adjustControls() {
        #expect(AdjustControl.allCases.count == 11)
        var probe = FilterParams.neutral
        for control in AdjustControl.allCases { probe[keyPath: control.keyPath] = 0.5 }
        #expect(probe.vignette == 0 && probe.blurVignette == 0 && probe.grain == 0)
        #expect(probe.tint == 0.5 && probe.tone == 0.5 && probe.refocus == 0.5)
        #expect(AdjustControl.sharpness.range == 0...1 && AdjustControl.refocus.range == 0...1)
        #expect(AdjustControl.exposure.range == -1...1)
    }

    @Test("untouched resolves to neutral and is the identity")
    func untouched() {
        #expect(PhotoLook.untouched.resolve() == .neutral)
        #expect(PhotoLook.untouched.isIdentity)
        #expect(!PhotoLook.untouched.hasFilterChanges && !PhotoLook.untouched.hasAdjustChanges)
    }

    @Test("intensity 1 is the preset, intensity 0 is the photo plus the extras")
    func intensity() {
        var look = PhotoLook.untouched
        look.select(.mono)
        #expect(look.resolve() == Self.withExtras(FilterPreset.mono.params))
        look.intensity = 0
        #expect(look.resolve() == Self.withExtras(.neutral))
        look.intensity = 0.5
        #expect(look.resolve().saturation == -0.5)
    }

    @Test("picking a preset fills only the extras nobody moved, and never on Original")
    func extrasDefaults() {
        var look = PhotoLook.untouched
        look.select(nil)
        #expect(look.value(of: .grain) == 0)
        look.set(.grain, to: 0.7)
        look.select(.soft)
        #expect(look.value(of: .grain) == 0.7)
        #expect(look.value(of: .vignette) == 0.1 && look.value(of: .blur) == 0.2)
        look.set(.vignette, to: 0)
        look.select(.lift)
        #expect(look.value(of: .vignette) == 0)
    }

    @Test("extras never move with the preset or the intensity")
    func extrasIndependent() {
        var look = PhotoLook.untouched
        look.select(.vivid)
        look.set(.blur, to: 0.55)
        look.intensity = 0.3
        look.select(.memory)
        look.select(nil)
        #expect(look.resolve().blurVignette == 0.55)
    }

    @Test("an Adjust offset adds to the preset and clamps to the range")
    func adjustAddsAndClamps() {
        var look = PhotoLook.untouched
        look.select(.lift)
        look.adjust.exposure = 0.1
        #expect(abs(look.resolve().exposure - 0.45) < 1e-9)
        look.adjust.exposure = 1
        #expect(look.resolve().exposure == 1)
        look.adjust.sharpness = -1
        #expect(look.resolve().sharpness == 0)
        #expect(look.hasAdjustChanges)
    }

    @Test("each tool resets on its own")
    func resets() {
        var look = PhotoLook.untouched
        look.select(.vivid)
        look.adjust.contrast = 0.2
        look.resetFilter()
        #expect(!look.hasFilterChanges && look.hasAdjustChanges)
        #expect(look.resolve().contrast == 0.2)
        look.select(.vivid)
        #expect(look.value(of: .grain) == 0.4)
        look.resetAdjust()
        #expect(!look.hasAdjustChanges)
    }
}
