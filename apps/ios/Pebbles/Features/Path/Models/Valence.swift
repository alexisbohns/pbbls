import Foundation

/// The 9-option valence picker shown in the create-pebble form.
/// Maps to the `pebbles.positiveness` and `pebbles.intensity` columns on save.
enum Valence: String, CaseIterable, Identifiable, Hashable {
    case lowlightSmall, lowlightMedium, lowlightLarge
    case neutralSmall, neutralMedium, neutralLarge
    case highlightSmall, highlightMedium, highlightLarge

    var id: String { rawValue }

    var label: String {
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
