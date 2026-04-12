import SwiftUI
import SafariServices

/// Identifies which legal document is currently shown in the auth sheet.
/// `Identifiable` so it can drive `.sheet(item:)`.
enum LegalDoc: String, Identifiable {
    case terms
    case privacy

    var id: String { rawValue }

    var url: URL {
        switch self {
        case .terms:   return URL(string: "https://www.pbbls.app/docs/terms")!
        case .privacy: return URL(string: "https://www.pbbls.app/docs/privacy")!
        }
    }
}

/// Thin SwiftUI wrapper around `SFSafariViewController`. Presents a legal
/// document in the same in-app Safari used by Mail and Messages. The sheet
/// dismisses via Safari's built-in "Done" button — no custom chrome.
struct LegalDocumentSheet: UIViewControllerRepresentable {
    let url: URL

    func makeUIViewController(context: Context) -> SFSafariViewController {
        SFSafariViewController(url: url)
    }

    func updateUIViewController(_ uiViewController: SFSafariViewController, context: Context) {
        // SFSafariViewController is immutable after init — nothing to update.
    }
}
