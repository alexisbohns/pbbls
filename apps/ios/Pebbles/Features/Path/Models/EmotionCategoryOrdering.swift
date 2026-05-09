import Foundation

/// Static curation data: the order in which the 7 emotion categories
/// surface in `EmotionPickerSheet`, indexed by the form's currently-selected
/// `Valence`.
///
/// The valence enum lives only in iOS, so this ordering deliberately lives
/// in iOS-only code rather than in the database. If a second client picks
/// up this UX later, the table-form is sketched in the spec.
///
/// Ordering rule (informally): own polarity first, opposite polarity last.
/// Within each polarity, the "peak" member leads at LARGE, the most
/// "subtle" member leads at SMALL, balanced at MEDIUM.
enum EmotionCategoryOrdering {

    struct Key: Hashable {
        let size: ValenceSizeGroup
        let polarity: ValencePolarity

        init(_ size: ValenceSizeGroup, _ polarity: ValencePolarity) {
            self.size = size
            self.polarity = polarity
        }
    }

    static let byValence: [Key: [String]] = [
        // HIGHLIGHTS — pleasant first
        Key(.large,  .highlight): ["pride",   "joy",     "peace",   "fear",    "anger",   "shame",   "sadness"],
        Key(.medium, .highlight): ["joy",     "pride",   "peace",   "fear",    "anger",   "shame",   "sadness"],
        Key(.small,  .highlight): ["peace",   "joy",     "pride",   "shame",   "sadness", "fear",    "anger"],

        // NEUTRALS — balanced, peace leads
        Key(.large,  .neutral):   ["peace",   "joy",     "pride",   "fear",    "anger",   "shame",   "sadness"],
        Key(.medium, .neutral):   ["peace",   "fear",    "joy",     "anger",   "pride",   "shame",   "sadness"],
        Key(.small,  .neutral):   ["peace",   "anger",   "joy",     "fear",    "pride",   "sadness", "shame"],

        // LOWLIGHTS — unpleasant first
        Key(.large,  .lowlight):  ["sadness", "fear",    "anger",   "shame",   "peace",   "joy",     "pride"],
        Key(.medium, .lowlight):  ["anger",   "fear",    "shame",   "sadness", "peace",   "pride",   "joy"],
        Key(.small,  .lowlight):  ["shame",   "sadness", "fear",    "anger",   "peace",   "pride",   "joy"],
    ]

    /// Used when no valence is selected on the draft yet. Equal to Medium Neutral.
    static let `default`: [String] = ["peace", "fear", "joy", "anger", "pride", "shame", "sadness"]

    static func order(for valence: Valence?) -> [String] {
        guard let v = valence else { return `default` }
        return byValence[Key(v.sizeGroup, v.polarity)] ?? `default`
    }
}
