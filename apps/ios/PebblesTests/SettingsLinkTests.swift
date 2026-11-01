import Foundation
import Testing
@testable import Pebbles

/// The decoder's only inputs are the live `settings_links` payloads
/// (`packages/supabase/fixtures/`, copied verbatim into `Fixtures/`).
@Suite("SettingsLink")
struct SettingsLinkTests {
    /// Read through `#filePath`, like the other suites' fixtures, never from the bundle.
    private static let fixtures = URL(fileURLWithPath: #filePath)
        .deletingLastPathComponent()
        .appendingPathComponent("Fixtures")

    private func fixture(_ name: String) throws -> Data {
        try Data(contentsOf: Self.fixtures.appendingPathComponent("\(name).json"))
    }

    @Test func decodesTheDatabasePayload() throws {
        let links = try SettingsLink.decodeList(fixture("settings-links"))
        #expect(links.map(\.key) == ["credits", "terms", "privacy", "legal-notice", "support"])
        #expect(links.first { $0.key == "support" }?.url.scheme == "mailto")
        #expect(links.first { $0.key == "support" }?.opensInApp == false)
        #expect(links.first { $0.key == "terms" }?.opensInApp == true)
    }

    @Test func frenchFallsBackToEnglish() throws {
        let links = try SettingsLink.decodeList(fixture("settings-links-edge"))
        let credits = try #require(links.first { $0.section == .about })
        #expect(credits.title(for: Locale(identifier: "fr_FR")) == "Credits")
    }

    @Test func readsTheFrenchTitleWhenPresent() throws {
        let links = try SettingsLink.decodeList(fixture("settings-links"))
        let credits = try #require(links.first { $0.key == "credits" })
        #expect(credits.title(for: Locale(identifier: "fr_FR")) == "Crédits")
        #expect(credits.title(for: Locale(identifier: "en_US")) == "Credits")
    }

    @Test func dropsUnknownSectionsAndSchemes() throws {
        let links = try SettingsLink.decodeList(fixture("settings-links-edge"))
        #expect(links.map(\.key) == ["credits"])
        #expect(links.allSatisfy { ["https", "mailto"].contains($0.url.scheme ?? "") })
    }

    @Test func builtInListIsTheSeed() throws {
        #expect(SettingsLink.builtIn.map(\.key) == ["credits", "terms", "privacy", "legal-notice", "support"])
    }

    @Test func groupsInFixedOrder() throws {
        let groups = SettingsLink.grouped(try SettingsLink.decodeList(fixture("settings-links")))
        #expect(groups.map(\.section) == [.legal, .about, .support])
        #expect(groups.first?.links.map(\.key) == ["terms", "privacy", "legal-notice"])
    }
}
