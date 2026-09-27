import Testing
import Foundation
@testable import Pebbles

@Suite("StoneLabSettings")
struct StoneLabSettingsTests {

    private func scratchDefaults() -> UserDefaults {
        let name = "StoneLabSettingsTests.\(UUID().uuidString)"
        let defaults = UserDefaults(suiteName: name)!
        defaults.removePersistentDomain(forName: name)
        return defaults
    }

    @Test("a fresh store yields the starting tables")
    func fresh() {
        let settings = StoneLabSettings.load(from: scratchDefaults())
        #expect(settings.material(for: .lowlight) == .starting(for: .lowlight))
        #expect(settings.tones(for: .highlight) == .starting(for: .highlight))
    }

    @Test("a changed knob survives a save and a load")
    func roundTrip() {
        let defaults = scratchDefaults()
        var settings = StoneLabSettings()
        var material = StoneMaterial.starting(for: .neutral)
        material.scale = 0.42
        settings.materials[ValencePolarity.neutral.rawValue] = material
        settings.tones[ValencePolarity.neutral.rawValue] = StoneTones(
            body: .init(slot: .light, opacity: 0.5), ink: .init(slot: .dark, opacity: 1),
            lipLight: .init(slot: .surface, opacity: 1), lipShadow: .init(slot: .shaded, opacity: 0.3)
        )
        settings.paletteIndex = 5
        settings.save(to: defaults)

        let loaded = StoneLabSettings.load(from: defaults)
        #expect(loaded == settings)
        #expect(loaded.material(for: .neutral).scale == 0.42)
        #expect(loaded.tones(for: .neutral).body.slot == .light)
        // Untouched polarities still fall back to their starting tables.
        #expect(loaded.material(for: .lowlight) == .starting(for: .lowlight))
    }

    @Test("unreadable data starts fresh instead of crashing")
    func corrupt() {
        let defaults = scratchDefaults()
        defaults.set(Data("nope".utf8), forKey: StoneLabSettings.defaultsKey)
        #expect(StoneLabSettings.load(from: defaults) == StoneLabSettings())
    }

    @Test("the summary names every polarity and the lowlight pick")
    func summary() {
        let text = StoneLabSettings().summary
        #expect(text.contains("[lowlight] blackstone"))
        #expect(text.contains("[neutral] river"))
        #expect(text.contains("[highlight] gem"))
        #expect(text.contains("scale 0.05 relief 3.00 contrast 1.00 sheen 0.05"))
        #expect(text.contains("body shaded@1.00 ink shaded@1.00 lipLight secondary@1.00"))
    }
}
