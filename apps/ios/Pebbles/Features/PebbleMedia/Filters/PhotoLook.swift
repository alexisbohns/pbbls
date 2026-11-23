import Foundation

/// What the Filter and Adjust tools hold for one photo (spec 2026-11-23 "The knobs",
/// "Adjust"). Resolved value per control: preset × intensity + Adjust offset, clamped.
/// Vignette, blur and grain are the person's own and never follow the preset.
struct PhotoLook: Equatable, Sendable {
    enum Extra: CaseIterable, Sendable {
        case vignette, blur, grain

        /// Set the first time a preset is picked, unless the person moved this extra already.
        var defaultValue: Double {
            switch self {
            case .vignette: 0.1
            case .blur: 0.2
            case .grain: 0.4
            }
        }

        var label: LocalizedStringResource {
            switch self {
            case .vignette: "Vignette"
            case .blur: "Blur"
            case .grain: "Grain"
            }
        }
    }

    private(set) var preset: FilterPreset?
    var intensity: Double = 1
    /// Offsets on top of preset × intensity. Only `AdjustControl` key paths are read.
    var adjust = FilterParams.neutral
    private var vignette: Double = 0
    private var blur: Double = 0
    private var grain: Double = 0
    private var touched: Set<Extra> = []

    static let untouched = PhotoLook()

    var hasFilterChanges: Bool { preset != nil || Extra.allCases.contains { value(of: $0) != 0 } }
    var hasAdjustChanges: Bool { adjust != .neutral }
    var isIdentity: Bool { resolve() == .neutral }

    func value(of extra: Extra) -> Double {
        switch extra {
        case .vignette: vignette
        case .blur: blur
        case .grain: grain
        }
    }

    /// `nil` is Original. Picking a preset fills the extras nobody has moved yet.
    mutating func select(_ preset: FilterPreset?) {
        self.preset = preset
        guard preset != nil else { return }
        for extra in Extra.allCases where !touched.contains(extra) {
            store(extra.defaultValue, in: extra)
        }
    }

    mutating func set(_ extra: Extra, to value: Double) {
        touched.insert(extra)
        store(min(max(value, 0), 1), in: extra)
    }

    mutating func resetFilter() {
        preset = nil
        intensity = 1
        touched = []
        for extra in Extra.allCases { store(0, in: extra) }
    }

    mutating func resetAdjust() { adjust = .neutral }

    func resolve() -> FilterParams {
        var params = FilterParams.neutral
        let strength = min(max(intensity, 0), 1)
        for control in AdjustControl.allCases {
            let base = (preset?.params[keyPath: control.keyPath] ?? 0) * strength
            let sum = base + adjust[keyPath: control.keyPath]
            params[keyPath: control.keyPath] = min(max(sum, control.range.lowerBound), control.range.upperBound)
        }
        params.vignette = vignette
        params.blurVignette = blur
        params.grain = grain
        return params
    }

    private mutating func store(_ value: Double, in extra: Extra) {
        switch extra {
        case .vignette: vignette = value
        case .blur: blur = value
        case .grain: grain = value
        }
    }
}
