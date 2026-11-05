import Foundation
import Testing
@testable import Pebbles

/// The versions iOS records must be the versions of the documents it links to.
/// The documents live in the web workspace (`apps/web/docs`), read in place
/// through `#filePath`, so a policy bump that forgets iOS fails here rather
/// than recording the wrong version into an accountability ledger. Mirrors
/// Android's `LegalVersionsTest`.
@Suite("Legal versions")
struct LegalVersionsTests {
    static let docs = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent() // Services
        .deletingLastPathComponent() // PebblesTests
        .deletingLastPathComponent() // apps/ios
        .deletingLastPathComponent() // apps
        .appendingPathComponent("web/docs")

    static func frontmatterVersion(_ path: String) throws -> String {
        let text = try String(contentsOf: docs.appendingPathComponent(path), encoding: .utf8)
        let lines = text.components(separatedBy: .newlines)
        let start = try #require(lines.firstIndex { $0.trimmingCharacters(in: .whitespaces) == "---" })
        let body = lines[(start + 1)...].prefix { $0.trimmingCharacters(in: .whitespaces) != "---" }
        let line = try #require(body.first { $0.hasPrefix("version:") }, "no version in \(path)")
        return line.dropFirst("version:".count).trimmingCharacters(in: .whitespaces)
    }

    @Test("terms version matches the published Terms of Service")
    func termsMatch() throws {
        #expect(try Self.frontmatterVersion("terms/en.md") == LegalVersions.terms)
    }

    @Test("privacy version matches the published Privacy Policy")
    func privacyMatch() throws {
        #expect(try Self.frontmatterVersion("privacy/en.md") == LegalVersions.privacy)
    }

    @Test("the French documents carry the same versions")
    func frenchMatch() throws {
        #expect(try Self.frontmatterVersion("terms/fr.md") == LegalVersions.terms)
        #expect(try Self.frontmatterVersion("privacy/fr.md") == LegalVersions.privacy)
    }
}
