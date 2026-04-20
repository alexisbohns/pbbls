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

    /// Asset name in `Assets.xcassets/Valence/`. Always non-empty.
    var assetName: String { "valence-\(rawValue)" }

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
