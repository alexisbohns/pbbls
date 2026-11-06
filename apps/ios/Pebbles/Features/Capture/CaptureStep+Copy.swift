import Foundation

extension CaptureStep {
    var title: LocalizedStringResource {
        switch self {
        case .photo:  return "Start with a picture"
        case .moment: return "Your moment"
        }
    }

    /// This step's title, or on the moment page the prompt the onboarding's hand-off chose (#1510 D9).
    func title(prompt: LocalizedStringResource?) -> LocalizedStringResource {
        self == .moment ? prompt ?? title : title
    }

    var subtitle: LocalizedStringResource? {
        switch self {
        case .photo:  return "Pick one, or write it from memory."
        case .moment: return nil
        }
    }
}
