import Foundation

enum Visibility: String, CaseIterable, Identifiable, Hashable, Decodable {
    // M51 privacy grades: secret = owner-only (the default since the backfill),
    // private = owner + mutual connections, public = anyone with the /p link.
    case secret = "secret"
    case `private` = "private"
    case `public` = "public"

    var id: String { rawValue }

    var label: LocalizedStringResource {
        switch self {
        case .secret:  return "Secret"
        case .private: return "Connections"
        case .public:  return "Public"
        }
    }

    /// What the grade means, as the Complete flow's privacy step and the held
    /// card's footer say it (#1146): one source for both.
    var explanation: LocalizedStringResource {
        switch self {
        case .secret:  return "Only you can see this one."
        case .private: return "Your mutual connections can see it."
        case .public:  return "Anyone with the link can see it."
        }
    }

    /// SF Symbol per grade — mirrored by the Android badge/menu drawables.
    var systemImageName: String {
        switch self {
        case .secret:  return "lock.fill"
        case .private: return "person.2.fill"
        case .public:  return "globe"
        }
    }
}
