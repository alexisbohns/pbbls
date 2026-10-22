import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D3: the fixture week's days in slices (PNGs stay under 1 MB, B2
    /// §Risks: Monday and Tuesday apart, Friday with Saturday, Sunday alone),
    /// every entry cut, the cards, the glyph-less stone, the bare and untitled
    /// moments and the still Friday. Android: SpineFeedPicnic*, SpineWeekDays*.
    @Suite("Spine feed references")
    @MainActor
    struct SpineFeedReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func monday(scheme: ReferenceScheme) async throws {
            ReferenceImage.assertMatches(try await stack(0...0), named: "monday",
                                         config: ReferenceConfig(scheme: scheme))
        }

        /// At `.accessibility2` the label grows past the spec's 50 pt pad, so the
        /// pad grows with it (maintainer decision, 2026-10-22): the label's text
        /// stays clear of the large gem's rings and of Tuesday's seam.
        @Test
        func mondayAx2() async throws {
            ReferenceImage.assertMatches(try await stack(0...0), named: "monday",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func tuesday(scheme: ReferenceScheme) async throws {
            ReferenceImage.assertMatches(try await stack(1...1), named: "tuesday",
                                         config: ReferenceConfig(scheme: scheme))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func wednesday(scheme: ReferenceScheme) async throws {
            ReferenceImage.assertMatches(try await stack(2...2), named: "wednesday",
                                         config: ReferenceConfig(scheme: scheme))
        }

        /// Thursday: the fourteen-soul picnic's people line, and the rooftop card.
        @Test(arguments: ReferenceScheme.allCases)
        func picnic(scheme: ReferenceScheme) async throws {
            ReferenceImage.assertMatches(try await stack(3...3), named: "picnic",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        /// The still Friday, then Saturday: the entry, the seawall portrait, the untitled photo.
        @Test(arguments: ReferenceScheme.allCases)
        func friSat(scheme: ReferenceScheme) async throws {
            ReferenceImage.assertMatches(try await stack(4...5), named: "friSat",
                                         config: ReferenceConfig(scheme: scheme))
        }

        /// Sunday: the bare photo's grey card, then Bruno's uncarved stone, the week's last day.
        @Test(arguments: ReferenceScheme.allCases)
        func sunday(scheme: ReferenceScheme) async throws {
            ReferenceImage.assertMatches(try await stack(6...6), named: "sunday",
                                         config: ReferenceConfig(scheme: scheme))
        }

        private func stack(_ days: ClosedRange<Int>) async throws -> some View {
            let model = await SpineFixtureWeek.model()
            let page = try #require(model.page(for: SpineFixtureWeek.weekStart))
            return SpineWeekStack(context: SpineFixtureWeek.context(model, page: page), days: days)
                .environment(\.spineSnapSource, SpineFixturePhotos.source)
        }
    }
}
