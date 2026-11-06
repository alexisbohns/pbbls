import SwiftUI
import SafariServices

/// Identifies which legal document is currently shown in the auth sheet.
/// `Identifiable` so it can drive `.sheet(item:)`.
enum LegalDoc: String, Identifiable {
    case terms
    case privacy

    var id: String { rawValue }

    /// The page in the app's language: the site otherwise follows its own stored choice, and
    /// `?lang=` overrides it. Anything but French falls to English.
    var url: URL { url(french: Locale.current.prefersFrench) }

    func url(french: Bool) -> URL {
        let path: String
        switch self {
        case .terms: path = "terms"
        case .privacy: path = "privacy"
        }
        return URL(string: "https://www.pbbls.app/docs/\(path)?lang=\(french ? "fr" : "en")")!
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
