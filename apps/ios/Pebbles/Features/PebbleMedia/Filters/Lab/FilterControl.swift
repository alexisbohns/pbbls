#if DEBUG
import Foundation

/// The lab's sliders, in the pipeline's order. Debug labels, never localized.
enum FilterControl: String, CaseIterable, Identifiable {
    case exposure, brilliance, highlights, shadows, contrast, saturation, vibrance
    case tone, tint, sharpness, refocus, vignette, blurVignette, grain

    var id: String { rawValue }

    var label: String {
        switch self {
        case .blurVignette: "Blur vignette"
        case .shadows: "Shadows (lowlight)"
        case .tone: "Tone (warmth)"
        default: rawValue.prefix(1).uppercased() + rawValue.dropFirst()
        }
    }

    var keyPath: WritableKeyPath<FilterParams, Double> {
        switch self {
        case .exposure: \.exposure
        case .brilliance: \.brilliance
        case .highlights: \.highlights
        case .shadows: \.shadows
        case .contrast: \.contrast
        case .saturation: \.saturation
        case .vibrance: \.vibrance
        case .tone: \.tone
        case .tint: \.tint
        case .sharpness: \.sharpness
        case .refocus: \.refocus
        case .vignette: \.vignette
        case .blurVignette: \.blurVignette
        case .grain: \.grain
        }
    }

    var range: ClosedRange<Double> {
        switch self {
        case .sharpness, .refocus, .blurVignette, .grain: 0...1
        default: -1...1
        }
    }
}
#endif
