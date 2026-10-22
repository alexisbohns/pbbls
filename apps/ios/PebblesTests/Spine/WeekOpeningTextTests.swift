import Foundation
import Testing
@testable import Pebbles

/// #1130 R18: the fixture week opens "the forty-first week · 2026", "The fifth
/// to the eleventh of October", "Thirteen pebbles, fourteen people." (Android
/// WeekOpeningTextTest), and an empty week says so.
@Suite("Week opening text")
@MainActor
struct WeekOpeningTextTests {
    typealias F = SpineFixtureWeek

    private func page(_ locale: Locale, moments: [MomentSummary] = F.moments) async throws -> SpinePage {
        let model = PathModel(calendar: F.calendar, now: { F.today }, locale: { locale })
        await model.load { moments }
        return try #require(model.page(for: F.weekStart))
    }

    @Test("the fixture week in English")
    func english() async throws {
        let text = WeekOpeningText(page: try await page(Vectors.en), locale: Vectors.en)
        #expect(text.kicker == "the forty-first week · 2026")
        #expect(text.title == "The fifth to the eleventh of October")
        #expect(text.summary == "Thirteen pebbles, fourteen people.")
    }

    @Test("the fixture week in French")
    func french() async throws {
        let text = WeekOpeningText(page: try await page(Vectors.fr), locale: Vectors.fr)
        #expect(text.kicker == "la quarante et unième semaine · 2026")
        #expect(text.title == "Du cinq au onze octobre")
        #expect(text.summary == "Treize galets, quatorze personnes.")
    }

    @Test("an empty current week has no pebbles and names no one")
    func empty() async throws {
        let text = WeekOpeningText(page: try await page(Vectors.en, moments: []), locale: Vectors.en)
        #expect(text.summary == "No pebbles.")
    }
}
