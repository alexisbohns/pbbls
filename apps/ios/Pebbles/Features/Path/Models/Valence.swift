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

    /// The lockup under the fan naming the stone the user picked: the polarity
    /// word in the hand font, and the span it covers. Two separate strings
    /// rather than one sentence because each line is typeset differently — and
    /// because a translator needs to move them independently.
    struct Headline {
        /// Rendered lowercase for small events, as authored otherwise.
        let word: LocalizedStringResource
        let span: LocalizedStringResource
    }

    var headline: Headline {
        Headline(word: headlineWord, span: headlineSpan)
    }

    /// "Moment" rather than `shortLabel`'s "Neutral" — the lockup reads as a
    /// phrase, and nobody calls their afternoon a neutral.
    private var headlineWord: LocalizedStringResource {
        switch polarity {
        case .lowlight:  return "Lowlight"
        case .neutral:   return "Moment"
        case .highlight: return "Highlight"
        }
    }

    private var headlineSpan: LocalizedStringResource {
        switch sizeGroup {
        case .small:  return "OF MY DAY"
        case .medium: return "OF MY WEEK"
        case .large:  return "OF MY MONTH"
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

// MARK: - The roll

/// Step geometry for the two-axis valence roll: polarity runs left → right,
/// size runs top → bottom (a big event sits above a small one, so reaching the
/// smaller ones means scrolling down the ladder).
///
/// Pure index arithmetic, kept off the view so the roll's behaviour can be
/// asserted without a gesture.
extension ValenceSizeGroup {
    /// Top to bottom, the order the roll stacks sizes in. Deliberately *not*
    /// `allCases`, which runs small → large.
    static let ladder: [ValenceSizeGroup] = [.large, .medium, .small]
}

extension Valence {
    /// The one case at a given cell. Total by construction: the 3×3 grid is
    /// covered, which `ValenceHelpersTests.lookupIsUnique` pins down.
    static func at(polarity: ValencePolarity, size: ValenceSizeGroup) -> Valence {
        for valence in Valence.allCases where valence.polarity == polarity && valence.sizeGroup == size {
            return valence
        }
        // Unreachable: every (polarity, size) pair has a case.
        return .neutralMedium
    }

    /// Position on each axis. Polarity uses `ValencePolarity.allCases` order,
    /// size uses the roll's top-to-bottom `ladder`.
    var polarityIndex: Int { ValencePolarity.allCases.firstIndex(of: polarity) ?? 1 }
    var sizeIndex: Int { ValenceSizeGroup.ladder.firstIndex(of: sizeGroup) ?? 1 }

    /// The valence at the given indices, clamped to the grid — the roll stops
    /// at the edges rather than wrapping, so a hard swipe cannot loop the user
    /// past the end and back to where they started.
    static func at(polarityIndex: Int, sizeIndex: Int) -> Valence {
        let polarity = ValencePolarity.allCases[
            min(max(polarityIndex, 0), ValencePolarity.allCases.count - 1)
        ]
        let size = ValenceSizeGroup.ladder[
            min(max(sizeIndex, 0), ValenceSizeGroup.ladder.count - 1)
        ]
        return .at(polarity: polarity, size: size)
    }

    /// The polarity one step to each side, nil at the ends. Drives the faded
    /// neighbour words the roll shows left and right.
    var polarityBefore: ValencePolarity? {
        polarityIndex > 0 ? ValencePolarity.allCases[polarityIndex - 1] : nil
    }

    var polarityAfter: ValencePolarity? {
        polarityIndex < ValencePolarity.allCases.count - 1
            ? ValencePolarity.allCases[polarityIndex + 1] : nil
    }
}
