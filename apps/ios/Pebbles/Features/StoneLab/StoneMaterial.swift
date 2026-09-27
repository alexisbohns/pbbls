#if DEBUG
import Foundation

/// The knobs of one surface. Every field is a shader uniform; `uniforms`
/// fixes their order so `StoneShaders` and `stone.metal` agree by test rather
/// than by eye.
struct StoneMaterial: Equatable, Codable {
    enum Kind: Int, CaseIterable, Identifiable, Codable {
        case lava, river, gem
        var id: Int { rawValue }
        var label: String {
            switch self {
            case .lava: return "Blackstone"
            case .river: return "River"
            case .gem: return "Gem"
            }
        }
    }

    var kind: Kind
    /// Grain frequency in cycles per stone unit (viewBox unit).
    var scale: Double
    /// Height-field depth for the normal. 0 is flat.
    var relief: Double
    /// How far the material may darken or lighten the body tone, 0..1.
    var contrast: Double
    /// Specular strength, 0..1.
    var sheen: Double
    /// Gem: facets per stone unit multiplier.
    var facetDensity: Double
    /// Gem: glitter point density, 0..1.
    var glitter: Double
    /// Blackstone: vesicle (pit) density, 0..1.
    var pits: Double
    /// River: sediment anisotropy, 0..1.
    var banding: Double
    /// Rim light width on the stone's own edge, in points. 0 is off.
    var rimWidth: Double = 4
    /// Rim light and shadow strength, 0..1.
    var rimStrength: Double = 0.6
    /// Carving lip width in points.
    var lipWidth: Double
    /// Carving lip strength, 0..1.
    var lipOpacity: Double

    /// Material uniforms in the order `stone.metal` declares them after the
    /// light, the tones and the lip-light opacity: kind, scale, relief, contrast, sheen,
    /// facetDensity, glitter, pits, banding, rimWidth, rimStrength.
    var uniforms: [Float] {
        [Float(kind.rawValue), Float(scale), Float(relief), Float(contrast), Float(sheen),
         Float(facetDensity), Float(glitter), Float(pits), Float(banding),
         Float(rimWidth), Float(rimStrength)]
    }

    /// The maintainer's picks on the phone (2026-09-27, second round),
    /// pasted from the lab's Copy button.
    static func starting(for polarity: ValencePolarity) -> StoneMaterial {
        switch polarity {
        case .lowlight:
            return StoneMaterial(kind: .lava, scale: 0.05, relief: 3, contrast: 1, sheen: 0.05,
                                 facetDensity: 1, glitter: 0, pits: 1, banding: 0,
                                 lipWidth: 0.5, lipOpacity: 0.84)
        case .neutral:
            return StoneMaterial(kind: .river, scale: 0.2, relief: 1.28, contrast: 0.64, sheen: 0.18,
                                 facetDensity: 1, glitter: 0, pits: 0, banding: 0.5,
                                 lipWidth: 1.2, lipOpacity: 0.7)
        case .highlight:
            return StoneMaterial(kind: .gem, scale: 0.11, relief: 1.94, contrast: 0.59, sheen: 0.35,
                                 facetDensity: 3, glitter: 1, pits: 0, banding: 0,
                                 lipWidth: 1.47, lipOpacity: 0.94)
        }
    }
}
#endif
