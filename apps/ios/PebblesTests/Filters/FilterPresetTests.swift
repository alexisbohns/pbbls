import CoreGraphics
import Testing
@testable import Pebbles

private typealias Img = FilterTestImages

@Suite("FilterPreset")
struct FilterPresetTests {
    @Test("seven presets, in the row's order")
    func order() {
        #expect(FilterPreset.allCases == [.everyday, .vivid, .soft, .lift, .mono, .monoDeep, .memory])
    }

    @Test("every preset stays inside each control's range and leaves the extras to the knobs",
          arguments: FilterPreset.allCases)
    func inRange(preset: FilterPreset) {
        let params = preset.params
        for control in AdjustControl.allCases {
            #expect(control.range.contains(params[keyPath: control.keyPath]), "\(control)")
        }
        #expect(params.vignette == 0 && params.blurVignette == 0 && params.grain == 0)
        #expect(params.refocus == 0 && !params.autoEnhance)
    }

    @Test("the black and white presets drop the colour", arguments: [FilterPreset.mono, .monoDeep])
    func monoIsGrey(preset: FilterPreset) throws {
        let image = try Img.render(preset.params, on: Img.ramp(size: 64))
        #expect(try Img.meanChroma(image) < 0.01)
    }

    @Test("Vivid raises the colour")
    func vividRaisesChroma() throws {
        let source = Img.ramp(size: 64)
        let image = try Img.render(FilterPreset.vivid.params, on: source)
        #expect(try Img.meanChroma(image) > Img.meanChroma(source))
    }
}
