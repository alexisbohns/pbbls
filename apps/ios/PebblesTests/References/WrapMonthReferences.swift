import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1226 W5: the month page on the harness's verbatim October (its ids given
    /// the fixture's faces): the cover with its garland, the sections (weeks,
    /// milestones, persons, domains), and the month sheet's pages as a ring and
    /// a calendar. Cut into slices, `ImageRenderer` blanking above 8,192 px.
    /// Android: `MonthPageScreenshots`.
    @Suite("Wrap month references")
    @MainActor
    struct WrapMonthReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func cover(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try Self.cover(), named: "cover",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        @Test
        func coverAx2() throws {
            ReferenceImage.assertMatches(try Self.cover(), named: "cover",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func sections(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try Self.weeksAndMilestones(), named: "weeks-milestones",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
            ReferenceImage.assertMatches(try Self.personsAndDomains(), named: "persons-domains",
                                         config: ReferenceConfig(scheme: scheme, locales: locales))
        }

        /// Where Dynamic Type squeezes names beside the fixed 40 pt rings and bars.
        @Test
        func sectionsAx2() throws {
            let config = ReferenceConfig(scheme: .paper, dynamicType: .accessibility2)
            ReferenceImage.assertMatches(try Self.weeksAndMilestones(), named: "weeks-milestones", config: config)
            ReferenceImage.assertMatches(try Self.personsAndDomains(), named: "persons-domains", config: config)
        }

        @Test(arguments: [MonthSection.persons, .gem, .domains])
        func sheetPage(tab: MonthSection) throws {
            let wrap = try MonthPayloadFixture.october()
            for view in [DayView.circle, .calendar] {
                let page = MonthSheetPage(tab: tab, wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap),
                                          dayView: .constant(view), showsSwitch: false)
                    .padding(.bottom, 24)
                    .background(Color.pebbles.surface)
                let schemes: [ReferenceScheme] = view == .circle ? ReferenceScheme.allCases : [.paper]
                for scheme in schemes {
                    let locales: Set<ReferenceLocale> = scheme == .paper && view == .circle ? [.en, .fr] : [.en]
                    ReferenceImage.assertMatches(page, named: "sheet-\(tab.rawValue)-\(view.rawValue)",
                                                 config: ReferenceConfig(scheme: scheme, locales: locales))
                }
            }
        }

        private static func weeksAndMilestones() throws -> some View {
            let wrap = try MonthPayloadFixture.october()
            return VStack(alignment: .leading, spacing: 0) {
                WeeksSummary(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap), onWeek: { _ in })
                MilestonesSection(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap), onOpen: { _ in })
            }
            .padding(.bottom, 24)
            .background(Color.pebbles.surface)
        }

        private static func personsAndDomains() throws -> some View {
            let wrap = try MonthPayloadFixture.october()
            return VStack(alignment: .leading, spacing: 0) {
                PersonsSection(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap), dayView: .circle, onMore: {})
                DomainsSection(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap), onMore: {})
            }
            .padding(.bottom, 24)
            .background(Color.pebbles.surface)
        }

        private static func cover() throws -> some View {
            let wrap = try MonthPayloadFixture.october()
            return MonthCoverView(wrap: wrap, lookups: MonthPayloadFixture.lookups(wrap))
                .padding(.bottom, 24)
                .background(Color.pebbles.surface)
        }
    }
}
