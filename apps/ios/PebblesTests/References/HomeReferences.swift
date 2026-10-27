import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// The Home greeting on a Monday morning in Paris, in the pass's language.
    @Suite("Home references") @MainActor
    struct HomeReferences {
        static func greeting() -> some View {
            let paris = TimeZone(identifier: "Europe/Paris")!
            let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime]
            let monday = f.date(from: "2026-11-02T08:10:00+01:00")!
            let locale = Locale(identifier: ReferenceLocale.current.rawValue)
            return HomeGreetingView(lines: HomeGreeting.lines(at: monday, name: "Alexis", zone: paris, locale: locale))
                .padding(Spacing.lg)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func greeting(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.greeting(), named: "greeting",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        static func shortcuts() -> some View {
            HomeShortcuts(onCamera: {}, onQuick: {}, onPebble: {})
                .padding(Spacing.lg)
                .frame(maxWidth: .infinity)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func shortcuts(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.shortcuts(), named: "shortcuts",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        static func toast() -> some View {
            MomentToast(
                landed: CameraMomentCoordinator.LandedMoment(
                    momentId: UUID(uuidString: "00000000-0000-0000-0000-000000000001")!,
                    shutter: Date(timeIntervalSince1970: 1_793_000_000), thumbnail: nil),
                onName: {}, onDismiss: {})
                .padding(Spacing.lg)
                .frame(width: 393)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func toast(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(Self.toast(), named: "toast",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        static func septemberFolded() throws -> HomeMonth {
            try #require(HomePayloads.wraps().months.first { $0.month == CalendarMonth(year: 2026, month: 9) })
        }

        static func lookups() throws -> WrapLookups {
            SpineFixtureMonth.lookups(try MonthPayloadFixture.october())
        }

        static func wrapCards() throws -> some View {
            let sept = try septemberFolded()
            let at = Date(timeIntervalSince1970: 1_793_000_000)
            let months = [
                sept,
                HomeMonth(month: .init(year: 2026, month: 8), count: 4, revealedAt: at, cover: sept.cover),
                HomeMonth(month: .init(year: 2026, month: 7), count: 4, revealedAt: at, cover: sept.cover)
            ]
            // The row's horizontal ScrollView does not render under ImageRenderer: draw its content.
            let lookups = try lookups()
            return VStack(alignment: .leading, spacing: Spacing.sm) {
                SpineSectionHeader(String(localized: "home.wraps.title")) {
                    Text(verbatim: "\(months.count)").spineType(.sectionLabel)
                }
                HStack(spacing: Spacing.sm) {
                    ForEach(months) { HomeWrapCard(month: $0, lookups: lookups) }
                }
            }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.md)
                .frame(width: 393, alignment: .leading)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func wrapCards(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try Self.wrapCards(), named: "wrapCards",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        static func wrapsEmpty() throws -> some View {
            VStack(alignment: .leading, spacing: Spacing.sm) {
                SpineSectionHeader(String(localized: "home.wraps.title")) {}
                HomeWrapPromise(liveMonth: CalendarMonth(year: 2026, month: 11))
            }
                .padding(.horizontal, Spacing.lg)
                .padding(.vertical, Spacing.md)
                .frame(width: 393, alignment: .leading)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func wrapsEmpty(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try Self.wrapsEmpty(), named: "wrapsEmpty",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        static func wrapSheetFolded() throws -> some View {
            HomeWrapFoldedView(month: try septemberFolded(), onUnfold: {}, isRevealing: false)
                .padding(Spacing.lg)
                .frame(width: 393, alignment: .leading)
                .background(Color.pebbles.surface)
        }

        @Test(arguments: ReferenceScheme.allCases)
        func wrapSheetFolded(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try Self.wrapSheetFolded(), named: "wrapSheetFolded",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }
    }
}
