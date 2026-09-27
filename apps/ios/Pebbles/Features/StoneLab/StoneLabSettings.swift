#if DEBUG
import Foundation
import os

/// Everything the lab's knobs hold, as one value: the palette, and a material
/// and a tone set per polarity. Saved to `UserDefaults` as JSON on every
/// change and restored on open, so a pick survives a restart. `summary` is
/// the same table as text for the clipboard, so a pick also survives the
/// phone.
///
/// Keyed by `ValencePolarity.rawValue` rather than the enum so the JSON is
/// a plain object and a missing polarity falls back to its starting table.
struct StoneLabSettings: Equatable, Codable {
    static let defaultsKey = "stoneLab.settings"
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "stone-lab")

    var paletteIndex: Int = 2 // joy: the warmest, easiest to judge
    var materials: [String: StoneMaterial] = [:]
    var tones: [String: StoneTones] = [:]
    /// Draw the engine's outline as a carving. Off means the stone's own edge
    /// is the only edge, lit by the rim.
    var showOutline: Bool = false
    /// Multiplier on the engine's carving scale inside the body (1 is the
    /// engine's 12% inset).
    var carvingScale: Double = 1.22
    /// Contact shadow under the stone, 0..1.
    var shadowStrength: Double = 0.8
    /// A carved line following the silhouette's own edge, in points; 0 is off.
    /// Drawn from the same wobbled path as the body, so it always aligns.
    var edgeWidth: Double = 1.88
    /// How far inside the edge that line sits, as a fraction of the stone.
    var edgeInset: Double = 0.03

    func material(for polarity: ValencePolarity) -> StoneMaterial {
        materials[polarity.rawValue] ?? .starting(for: polarity)
    }

    func tones(for polarity: ValencePolarity) -> StoneTones {
        tones[polarity.rawValue] ?? .starting(for: polarity)
    }

    // MARK: - Persistence

    static func load(from defaults: UserDefaults = .standard) -> StoneLabSettings {
        guard let data = defaults.data(forKey: defaultsKey) else { return StoneLabSettings() }
        do {
            return try JSONDecoder().decode(StoneLabSettings.self, from: data)
        } catch {
            logger.error("stone lab settings unreadable, starting fresh: \(error, privacy: .public)")
            return StoneLabSettings()
        }
    }

    func save(to defaults: UserDefaults = .standard) {
        do {
            defaults.set(try JSONEncoder().encode(self), forKey: Self.defaultsKey)
        } catch {
            Self.logger.error("stone lab settings not saved: \(error, privacy: .public)")
        }
    }

    // MARK: - Clipboard

    /// One line per knob, per polarity, in the order the page shows them.
    var summary: String {
        var lines = ["stone lab · palette \(StoneLabPalettes.all[min(max(paletteIndex, 0), StoneLabPalettes.all.count - 1)].slug)"]
        lines.append("  outline \(showOutline) carvingScale \(f(carvingScale)) shadow \(f(shadowStrength))"
                     + " edgeWidth \(f(edgeWidth)) edgeInset \(f(edgeInset))")
        for polarity in ValencePolarity.allCases {
            let m = material(for: polarity)
            let t = tones(for: polarity)
            lines.append("[\(polarity.rawValue)] \(m.kind.label.lowercased())")
            lines.append("  scale \(f(m.scale)) relief \(f(m.relief)) contrast \(f(m.contrast)) sheen \(f(m.sheen))")
            lines.append("  lipWidth \(f(m.lipWidth)) lipOpacity \(f(m.lipOpacity)) pits \(f(m.pits)) banding \(f(m.banding))"
                         + " facetDensity \(f(m.facetDensity)) glitter \(f(m.glitter))"
                         + " rimWidth \(f(m.rimWidth)) rimStrength \(f(m.rimStrength))")
            lines.append("  body \(pick(t.body)) ink \(pick(t.ink)) lipLight \(pick(t.lipLight)) lipShadow \(pick(t.lipShadow))")
        }
        return lines.joined(separator: "\n")
    }

    /// Fixed "0.00", not the user's locale: the text is pasted back into
    /// Swift source, so a decimal comma would not compile.
    private func f(_ value: Double) -> String {
        String(format: "%.2f", value)
    }

    private func pick(_ pick: StoneTones.Pick) -> String {
        "\(pick.slot.rawValue)@\(f(pick.opacity))"
    }
}
#endif
