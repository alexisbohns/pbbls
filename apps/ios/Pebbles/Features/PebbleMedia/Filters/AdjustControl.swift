import Foundation

/// The Adjust tool's sliders (spec 2026-11-23 "Adjust"), in pipeline order. Each one
/// writes one `FilterParams` value; vignette, blur and grain belong to the Filter tool.
enum AdjustControl: CaseIterable, Identifiable, Sendable {
    case exposure, brilliance, highlights, shadows, contrast, saturation, vibrance, warmth, tint, sharpness, refocus

    var id: Self { self }

    var keyPath: WritableKeyPath<FilterParams, Double> {
        switch self {
        case .exposure: \.exposure
        case .brilliance: \.brilliance
        case .highlights: \.highlights
        case .shadows: \.shadows
        case .contrast: \.contrast
        case .saturation: \.saturation
        case .vibrance: \.vibrance
        case .warmth: \.tone
        case .tint: \.tint
        case .sharpness: \.sharpness
        case .refocus: \.refocus
        }
    }

    var range: ClosedRange<Double> {
        switch self {
        case .sharpness, .refocus: 0...1
        default: -1...1
        }
    }

    var label: LocalizedStringResource {
        switch self {
        case .exposure: "Exposure"
        case .brilliance: "Brilliance"
        case .highlights: "Highlights"
        case .shadows: "Shadows"
        case .contrast: "Contrast"
        case .saturation: "Saturation"
        case .vibrance: "Vibrance"
        case .warmth: "Warmth"
        case .tint: "Tint"
        case .sharpness: "Sharpness"
        case .refocus: "Refocus"
        }
    }
}
