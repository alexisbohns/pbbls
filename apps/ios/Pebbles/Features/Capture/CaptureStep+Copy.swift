import Foundation

extension CaptureStep {
    var title: LocalizedStringResource {
        switch self {
        case .photo: return "Start with a picture"
        case .when:  return "When did it happen?"
        case .name:  return "What do you call it?"
        }
    }

    var subtitle: LocalizedStringResource? {
        switch self {
        case .photo: return "Or skip it and write from memory."
        case .when:  return nil
        case .name:  return "A name, a photo, or both."
        }
    }
}
