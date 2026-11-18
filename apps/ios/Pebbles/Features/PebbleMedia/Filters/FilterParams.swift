import Foundation

/// One look in the filter lab (spec 2026-11-17 "The controls"): thirteen
/// Photos-style controls plus two extras. Two-sided controls run −1…1,
/// one-sided ones (sharpness, blur vignette, grain, refocus) 0…1, and zero
/// everywhere is the identity.
struct FilterParams: Codable, Equatable, Sendable {
    var exposure: Double = 0
    var brilliance: Double = 0
    var highlights: Double = 0
    var shadows: Double = 0
    var contrast: Double = 0
    var saturation: Double = 0
    var vibrance: Double = 0
    var tone: Double = 0
    var tint: Double = 0
    var sharpness: Double = 0
    var vignette: Double = 0
    var blurVignette: Double = 0
    var grain: Double = 0
    var refocus: Double = 0
    var autoEnhance = false

    static let neutral = FilterParams()

    init() {}

    /// A missing key reads as neutral, so an export from an older lab still decodes.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func value(_ key: CodingKeys) throws -> Double { try c.decodeIfPresent(Double.self, forKey: key) ?? 0 }
        exposure = try value(.exposure)
        brilliance = try value(.brilliance)
        highlights = try value(.highlights)
        shadows = try value(.shadows)
        contrast = try value(.contrast)
        saturation = try value(.saturation)
        vibrance = try value(.vibrance)
        tone = try value(.tone)
        tint = try value(.tint)
        sharpness = try value(.sharpness)
        vignette = try value(.vignette)
        blurVignette = try value(.blurVignette)
        grain = try value(.grain)
        refocus = try value(.refocus)
        autoEnhance = try c.decodeIfPresent(Bool.self, forKey: .autoEnhance) ?? false
    }
}
