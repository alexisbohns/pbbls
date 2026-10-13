import Foundation

extension CompleteStep {
    var title: LocalizedStringResource {
        switch self {
        case .valence:    return "How did it land?"
        case .emotion:    return "What did you feel?"
        case .domain:     return "What part of life?"
        case .souls:      return "Anyone in this one?"
        case .collection: return "Add it to a collection?"
        case .privacy:    return "Who can see it?"
        case .success:    return "Your pebble"
        }
    }

    var subtitle: LocalizedStringResource? {
        switch self {
        case .valence: return "How much of your life did this take up?"
        case .emotion, .domain, .souls, .collection, .privacy, .success: return nil
        }
    }
}
