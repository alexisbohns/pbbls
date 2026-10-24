import Foundation
import Testing
@testable import Pebbles

/// #1226: every wrap key is in the catalogue in English and French and
/// resolves in both, and none went missing: the counts are Android's keys,
/// ported one for one (the tile's VoiceOver sentence split into a label and a hint).
@Suite("Wrap strings catalogue")
struct WrapCatalogueTests {
    @Test("every wrap key has an English and a French value",
          arguments: [("path.strip.", 5), ("path.wrap.", 35), ("path.month.", 24), ("path.gem.", 10)])
    func catalogue(prefix: String, count: Int) throws {
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .deletingLastPathComponent().appendingPathComponent("Pebbles/Resources/Localizable.xcstrings")
        let root = try #require(try JSONSerialization.jsonObject(with: Data(contentsOf: url)) as? [String: Any])
        let strings = try #require(root["strings"] as? [String: Any])
        let keys = strings.keys.filter { $0.hasPrefix(prefix) }
        #expect(keys.count == count)
        for key in keys {
            let entry = try #require(strings[key] as? [String: Any])
            let localizations = try #require(entry["localizations"] as? [String: Any], "\(key)")
            #expect(localizations["en"] != nil && localizations["fr"] != nil, "\(key) needs en and fr")
            #expect(SpineStrings.string(key, locale: Vectors.en) != key, "\(key) does not resolve in en")
            #expect(SpineStrings.string(key, locale: Vectors.fr) != key, "\(key) does not resolve in fr")
        }
    }
}
