import Foundation

/// The Filter tool's presets (spec 2026-11-23 "The presets"): the pipeline's values at
/// intensity 1, drawn from the filter lab's looks. Any control not set is 0.
enum FilterPreset: String, CaseIterable, Identifiable, Sendable {
    case everyday, vivid, soft, lift, mono, monoDeep, memory

    var id: Self { self }

    var name: LocalizedStringResource {
        switch self {
        case .everyday: "Everyday"
        case .vivid: "Vivid"
        case .soft: "Soft"
        case .lift: "Lift"
        case .mono: "Mono"
        case .monoDeep: "Mono Deep"
        case .memory: "Memory"
        }
    }

    var params: FilterParams {
        switch self {
        case .everyday:
            Self.make([\.exposure: 0.08, \.brilliance: -0.05, \.shadows: -0.19, \.contrast: 0.06,
                       \.saturation: -0.09, \.vibrance: 0.16, \.tone: -0.08, \.sharpness: 0.20])
        case .vivid:
            Self.make([\.exposure: 0.13, \.brilliance: 0.22, \.highlights: -0.04, \.shadows: -0.22, \.contrast: 0.11,
                       \.saturation: 0.10, \.vibrance: 0.30, \.tone: -0.25, \.sharpness: 0.22])
        case .soft:
            Self.make([\.exposure: 0.23, \.brilliance: 0.18, \.highlights: -0.26, \.shadows: 0.09,
                       \.vibrance: -0.08, \.tone: -0.29, \.sharpness: 0.30])
        case .lift:
            Self.make([\.exposure: 0.35, \.brilliance: 0.30, \.highlights: 0.36, \.shadows: -0.15, \.contrast: 0.09,
                       \.saturation: -0.21, \.vibrance: 0.22, \.tone: -0.10, \.sharpness: 0.38])
        case .mono:
            Self.make([\.exposure: 0.21, \.brilliance: 0.14, \.highlights: 0.25, \.shadows: -0.08, \.contrast: 0.12,
                       \.saturation: -1, \.sharpness: 0.29])
        case .monoDeep:
            Self.make([\.exposure: 0.18, \.brilliance: 0.35, \.highlights: 0.38, \.shadows: -0.32, \.contrast: 0.36,
                       \.saturation: -1, \.sharpness: 0.43])
        case .memory:
            Self.make([\.exposure: 0.20, \.brilliance: 0.15, \.highlights: -0.08, \.shadows: 0.15, \.contrast: -0.08,
                       \.saturation: -1, \.tone: 0.15, \.sharpness: 0.10])
        }
    }

    private static func make(_ values: [WritableKeyPath<FilterParams, Double>: Double]) -> FilterParams {
        var params = FilterParams()
        for (keyPath, value) in values { params[keyPath: keyPath] = value }
        return params
    }
}
