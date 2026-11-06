import Foundation

extension CompleteStep {
    var title: LocalizedStringResource {
        switch self {
        case .valence: return "How did it land?"
        case .emotion: return "What did you feel?"
        case .life:    return "What part of life?"
        case .success: return "Make it richer"
        }
    }

    /// The summary pill replaces the subtitles (D1); the steps carry none.
    var subtitle: LocalizedStringResource? {
        switch self {
        case .valence, .emotion, .life, .success: return nil
        }
    }
}
