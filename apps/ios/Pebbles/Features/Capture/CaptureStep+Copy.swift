import Foundation

extension CaptureStep {
    var title: LocalizedStringResource {
        switch self {
        case .photo:  return "Start with a picture"
        case .moment: return "Your moment"
        }
    }

    var subtitle: LocalizedStringResource? {
        switch self {
        case .photo:  return "Pick one, or write it from memory."
        case .moment: return nil
        }
    }
}
