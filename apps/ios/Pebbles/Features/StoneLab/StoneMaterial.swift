#if DEBUG
import Foundation

/// The knobs of one surface. Every field is a shader uniform; `uniforms`
/// fixes their order so `StoneShaders` and `stone.metal` agree by test rather
/// than by eye.
struct StoneMaterial: Equatable {
    enum Kind: Int, CaseIterable, Identifiable {
        case lava, river, gem
        var id: Int { rawValue }
        var label: String {
            switch self {
            case .lava: return "Lava"
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
    /// Lava: crack width threshold on the cell edge distance, 0..0.3.
    var crack: Double
    /// River: sediment anisotropy, 0..1.
    var banding: Double
    /// Carving lip width in points.
    var lipWidth: Double
    /// Carving lip strength, 0..1.
    var lipOpacity: Double

    /// Material uniforms in the order `stone.metal` declares them after the
    /// light and the tones: kind, scale, relief, contrast, sheen,
    /// facetDensity, glitter, crack, banding.
    var uniforms: [Float] {
        [Float(kind.rawValue), Float(scale), Float(relief), Float(contrast), Float(sheen),
         Float(facetDensity), Float(glitter), Float(crack), Float(banding)]
    }

    static func starting(for polarity: ValencePolarity) -> StoneMaterial {
        switch polarity {
        case .lowlight:
            return StoneMaterial(kind: .lava, scale: 0.045, relief: 1.6, contrast: 0.55, sheen: 0.05,
                                 facetDensity: 1, glitter: 0, crack: 0.06, banding: 0,
                                 lipWidth: 1.5, lipOpacity: 0.8)
        case .neutral:
            return StoneMaterial(kind: .river, scale: 0.35, relief: 0.6, contrast: 0.22, sheen: 0.18,
                                 facetDensity: 1, glitter: 0, crack: 0, banding: 0.5,
                                 lipWidth: 1.2, lipOpacity: 0.7)
        case .highlight:
            return StoneMaterial(kind: .gem, scale: 0.06, relief: 1.2, contrast: 0.45, sheen: 0.35,
                                 facetDensity: 1, glitter: 0.35, crack: 0, banding: 0,
                                 lipWidth: 1.2, lipOpacity: 0.9)
        }
    }
}
#endif
