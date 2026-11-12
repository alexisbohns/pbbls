import Foundation

/// What a report is about. A person is addressed by the route the reporter met
/// them by (their connection, or the invite they hold), never by user id: the
/// app never has one (standing rule), and `report_person` resolves it.
enum ReportTarget: Hashable, Sendable {
    case glyph(UUID)
    case connection(id: UUID, name: String?)
    case invite(token: String, name: String?)

    /// Only a connection can be removed and blocked in the same step.
    var offersBlock: Bool {
        if case .connection = self { return true }
        return false
    }

    var personName: String? {
        switch self {
        case .glyph: return nil
        case .connection(_, let name), .invite(_, let name):
            guard let name, !name.isEmpty else { return nil }
            return name
        }
    }

    var title: String {
        switch self {
        case .glyph:
            return String(localized: "Report this glyph")
        case .connection, .invite:
            if let name = personName { return String(localized: "Report \(name)") }
            return String(localized: "Report this person")
        }
    }

    var blockLabel: String {
        if let name = personName { return String(localized: "Also remove and block \(name)") }
        return String(localized: "Also remove and block this person")
    }
}
