import Foundation
import Testing
@testable import Pebbles

/// #1146 §Strings: the footer and the Complete flow's privacy step read the
/// same three sentences.
@Suite("Visibility explanation")
struct VisibilityExplanationTests {
    private func resolved(_ resource: LocalizedStringResource, _ language: String) -> String {
        var copy = resource
        copy.locale = Locale(identifier: language)
        return String(localized: copy)
    }

    @Test("each grade explains itself in English and French",
          arguments: [
              (Visibility.secret, "Only you can see this one."),
              (Visibility.private, "Your mutual connections can see it."),
              (Visibility.public, "Anyone with the link can see it.")
          ])
    func sentence(grade: Visibility, english: String) {
        #expect(resolved(grade.explanation, "en") == english)
        let french = resolved(grade.explanation, "fr")
        #expect(!french.isEmpty && french != english)
    }
}
