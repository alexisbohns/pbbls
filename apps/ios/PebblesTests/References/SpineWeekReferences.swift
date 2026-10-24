import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D4 (B2 §B2.6): the fixture week's verso (+ French Paper, + `-ax2`
    /// Paper), since #1226 its line and live tiles, the current week's mouth
    /// against a past week's (which links on), and the empty current week, its
    /// line alone with the way to a first pebble. Android: SpineWeekVerso*, SpineWeekMouthDark,
    /// SpineWeekEmpty*.
    @Suite("Spine week references")
    @MainActor
    struct SpineWeekReferences {
        typealias F = SpineFixtureWeek

        @Test(arguments: ReferenceScheme.allCases)
        func verso(scheme: ReferenceScheme) async throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try await stack(.verso), named: "verso",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test
        func versoAx2() async throws {
            ReferenceImage.assertMatches(try await stack(.verso), named: "verso",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func mouth(scheme: ReferenceScheme) async throws {
            ReferenceImage.assertMatches(try await stack(.closing), named: "mouth",
                                         config: ReferenceConfig(scheme: scheme))
        }

        /// A past week: the mouth links on to the next page, "week 41".
        @Test(arguments: ReferenceScheme.allCases)
        func mouthPast(scheme: ReferenceScheme) async throws {
            let august = MomentSummary(id: UUID(), name: "Late August", happenedAt: Vectors.at("2026-08-26T10:00:00Z"),
                                       createdAt: Vectors.at("2026-08-26T10:00:00Z"), firstSnapPath: nil, pebble: nil)
            let week = WeekRollBuilder.weekStart(for: august.happenedAt, calendar: F.calendar)
            ReferenceImage.assertMatches(try await stack(.closing, extra: [august], week: week), named: "mouthPast",
                                         config: ReferenceConfig(scheme: scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func empty(scheme: ReferenceScheme) async throws {
            let model = PathModel(calendar: F.calendar, now: { F.today })
            await model.load { [] }
            let page = try #require(model.page(for: F.weekStart))
            ReferenceImage.assertMatches(SpineWeekStack(context: F.context(model, page: page)), named: "empty",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        private func stack(
            _ part: SpineWeekStack.Part, extra: [MomentSummary] = [], week: Date = F.weekStart
        ) async throws -> some View {
            let model = PathModel(calendar: F.calendar, now: { F.today })
            await model.load { F.moments + extra }
            model.updateSouls(names: F.soulNames, glyphs: F.soulGlyphs)
            let page = try #require(model.page(for: week))
            // The fixture week is the live week: its line says "so far", its tiles are dashed.
            let wrap = week == F.weekStart ? F.weekWrap(live: true) : nil
            return SpineWeekStack(context: F.context(model, page: page, wrap: wrap), part: part)
                .environment(\.spineSnapSource, SpineFixturePhotos.source)
        }
    }
}
