import Foundation
import CoreGraphics

/// The 9-option valence picker shown in the create-pebble form.
/// Maps to the `pebbles.positiveness` and `pebbles.intensity` columns on save.
enum Valence: String, CaseIterable, Identifiable, Hashable {
    case lowlightSmall, lowlightMedium, lowlightLarge
    case neutralSmall, neutralMedium, neutralLarge
    case highlightSmall, highlightMedium, highlightLarge

    var id: String { rawValue }

    var label: LocalizedStringResource {
        switch self {
        case .lowlightSmall:   return "Lowlight — small"
        case .lowlightMedium:  return "Lowlight — medium"
        case .lowlightLarge:   return "Lowlight — large"
        case .neutralSmall:    return "Neutral — small"
        case .neutralMedium:   return "Neutral — medium"
        case .neutralLarge:    return "Neutral — large"
        case .highlightSmall:  return "Highlight — small"
        case .highlightMedium: return "Highlight — medium"
        case .highlightLarge:  return "Highlight — large"
        }
    }

    /// Maps to `pebbles.positiveness` (-1, 0, +1).
    var positiveness: Int {
        switch self {
        case .lowlightSmall, .lowlightMedium, .lowlightLarge:    return -1
        case .neutralSmall, .neutralMedium, .neutralLarge:       return 0
        case .highlightSmall, .highlightMedium, .highlightLarge: return 1
        }
    }

    /// Maps to `pebbles.intensity` (1, 2, 3).
    var intensity: Int {
        switch self {
        case .lowlightSmall, .neutralSmall, .highlightSmall:    return 1
        case .lowlightMedium, .neutralMedium, .highlightMedium: return 2
        case .lowlightLarge, .neutralLarge, .highlightLarge:    return 3
        }
    }
}

extension Valence {
    /// Rebuild a valence from the decomposed `(positiveness, intensity)` pair the
    /// wire and `pebble_drafts.payload` store (M47). Returns nil for any pair
    /// outside the 3×3 grid so a malformed draft leaves the picker unset rather
    /// than silently claiming a value the user never chose.
    ///
    /// Note: `PebbleDetail.valence` carries the same mapping inline for the
    /// non-optional read path; worth collapsing into this one day.
    static func from(positiveness: Int, intensity: Int) -> Valence? {
        switch (positiveness, intensity) {
        case (-1, 1): return .lowlightSmall
        case (-1, 2): return .lowlightMedium
        case (-1, 3): return .lowlightLarge
        case (0, 1):  return .neutralSmall
        case (0, 2):  return .neutralMedium
        case (0, 3):  return .neutralLarge
        case (1, 1):  return .highlightSmall
        case (1, 2):  return .highlightMedium
        case (1, 3):  return .highlightLarge
        default:      return nil
        }
    }
}

/// Groups the nine `Valence` cases by size for the picker sheet.
/// Drives the three section headers ("Day event" / "Week event" / "Month event").
enum ValenceSizeGroup: String, CaseIterable, Identifiable {
    case small, medium, large

    var id: String { rawValue }

    var name: LocalizedStringResource {
        switch self {
        case .small:  return "Day event"
        case .medium: return "Week event"
        case .large:  return "Month event"
        }
    }

    var description: LocalizedStringResource {
        switch self {
        case .small:
            return "This moment impacted my day and will be wrapped in my weekly Cairn"
        case .medium:
            return "This moment impacted my whole week and will be wrapped in my monthly Cairn"
        case .large:
            return "This moment impacted my whole month and will be wrapped in my yearly Cairn"
        }
    }
}

/// Drives the left-to-right ordering of options inside each picker section.
enum ValencePolarity: String, CaseIterable {
    case lowlight, neutral, highlight
}

extension Valence {
    var sizeGroup: ValenceSizeGroup {
        switch self {
        case .lowlightSmall, .neutralSmall, .highlightSmall:    return .small
        case .lowlightMedium, .neutralMedium, .highlightMedium: return .medium
        case .lowlightLarge, .neutralLarge, .highlightLarge:    return .large
        }
    }

    var polarity: ValencePolarity {
        switch self {
        case .lowlightSmall, .lowlightMedium, .lowlightLarge:    return .lowlight
        case .neutralSmall, .neutralMedium, .neutralLarge:       return .neutral
        case .highlightSmall, .highlightMedium, .highlightLarge: return .highlight
        }
    }

    /// Base name of this valence's artwork. Names both the source vector in
    /// `Assets.xcassets/Valence/` and the `ValenceArt/<name>.svg` the picker
    /// wobbles, which `Scripts/valence-art-to-svg.mjs` generates from it.
    var assetName: String { "valence-\(rawValue)" }

    /// Caption under the valence fan, naming the stone the user just picked.
    /// Warmer and more sentence-like than `label`, which is the terse
    /// "Lowlight — small" form the collapsed form row wants.
    var caption: LocalizedStringResource {
        switch self {
        case .lowlightSmall:   return "A small lowlight."
        case .lowlightMedium:  return "A medium lowlight."
        case .lowlightLarge:   return "A large lowlight."
        case .neutralSmall:    return "A small neutral moment."
        case .neutralMedium:   return "A medium neutral moment."
        case .neutralLarge:    return "A large neutral moment."
        case .highlightSmall:  return "A small highlight."
        case .highlightMedium: return "A medium highlight."
        case .highlightLarge:  return "A large highlight."
        }
    }

    /// Polarity-only label used inside an option button ("Lowlight" / "Neutral" / "Highlight").
    /// Use `label` when the size axis also matters (e.g. the collapsed form row).
    var shortLabel: LocalizedStringResource {
        switch polarity {
        case .lowlight:  return "Lowlight"
        case .neutral:   return "Neutral"
        case .highlight: return "Highlight"
        }
    }
}

extension ValenceSizeGroup {
    /// Render height in the detail / edit sheets. Scales small pebbles down
    /// so a small render doesn't visually dominate a medium or large one —
    /// addresses issue #286 "small pebbles are full width, they should be a
    /// little bit smaller".
    var renderHeight: CGFloat {
        switch self {
        case .small:  return 180
        case .medium: return 220
        case .large:  return 260
        }
    }
}
