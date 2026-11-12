import Foundation

/// Why something is reported. Raw values are the server's slugs (the
/// `content_reports.reason` CHECK), so they never change; the order is the
/// order the sheet lists them in.
enum ReportReason: String, CaseIterable, Identifiable, Sendable {
    case harassment
    case hate
    case impersonation
    case sexual
    case violence
    case selfHarm = "self_harm"
    case illegal
    case spam
    case other

    var id: String { rawValue }

    var label: LocalizedStringResource {
        switch self {
        case .harassment: return "Harassment"
        case .hate: return "Hate"
        case .impersonation: return "Impersonation"
        case .sexual: return "Sexual content"
        case .violence: return "Violence"
        case .selfHarm: return "Self-harm"
        case .illegal: return "Illegal"
        case .spam: return "Spam"
        case .other: return "Other"
        }
    }
}
