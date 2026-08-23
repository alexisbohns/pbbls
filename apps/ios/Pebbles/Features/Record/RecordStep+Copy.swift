import Foundation

// Exhaustive switches over an eleven-case enum are dispatch tables, not
// branching logic: the rule's threshold is calibrated for the latter. Keeping
// them exhaustive (rather than dictionary lookups with a fallback) is what
// makes the compiler tell us about a step whose copy was never written.
/// The record flow's per-step copy.
///
/// Lives on the step rather than in `RecordFlowView` to match the convention
/// `OnboardingStep` and `WelcomeStep` set: the step type carries its content
/// and the view layer renders it without branching on which step it is.
extension RecordStep {

    var title: LocalizedStringResource {
        switch self {
        case .photo:      return "Start with a picture"
        case .when:       return "When did it happen?"
        case .name:       return "What do you call it?"
        case .valence:    return "How did it land?"
        case .emotion:    return "What did you feel?"
        case .domain:     return "What part of life?"
        case .souls:      return "Anyone in this one?"
        case .collection: return "Add it to a collection?"
        case .glyph:      return "Give it a glyph"
        case .privacy:    return "Who can see it?"
        case .success:    return "Your pebble"
        }
    }

    /// Only the steps whose ask needs a second line carry one.
    var subtitle: LocalizedStringResource? {
        switch self {
        case .photo:   return "Or skip it and write from memory."
        case .valence: return "How much of your life did this take up?"
        case .glyph:   return "A little mark, just for this one."
        case .when, .name, .emotion, .domain, .souls,
             .collection, .privacy, .success:
            return nil
        }
    }
}
