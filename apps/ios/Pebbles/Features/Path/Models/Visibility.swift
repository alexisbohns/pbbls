import Foundation

enum Visibility: String, CaseIterable, Identifiable, Hashable, Decodable {
    case `private` = "private"
    case `public` = "public"

    var id: String { rawValue }

    var label: String {
        switch self {
        case .private: return "Private"
        case .public:  return "Public"
        }
    }
}
