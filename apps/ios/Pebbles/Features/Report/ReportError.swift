import Foundation

/// Postgres raises short slugs and PostgREST delivers them inside the message,
/// so the mapping is a substring match: the contract `ConnectionsError` uses.
enum ReportError: Error, Equatable {
    case notFound
    case ownContent
    case other(String)

    static func from(_ error: Error) -> ReportError {
        if let error = error as? ReportError { return error }
        let message = error.localizedDescription
        let lowered = message.lowercased()
        if lowered.contains("cannot_report_own") { return .ownContent }
        if lowered.contains("not_found") { return .notFound }
        return .other(message)
    }

    var message: String {
        switch self {
        case .notFound: return String(localized: "This is no longer available.")
        case .ownContent: return String(localized: "You can't report your own.")
        case .other: return String(localized: "Couldn't send the report. Try again.")
        }
    }
}
