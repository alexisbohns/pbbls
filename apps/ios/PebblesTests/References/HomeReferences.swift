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
    }
}
