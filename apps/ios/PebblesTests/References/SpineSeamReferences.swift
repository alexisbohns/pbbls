import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D3: Monday's seam with the day's top padding under it, then the
    /// still Friday, on the raked ground with the line: Paper and Night, English
    /// and French (the weekday), plus the still day at `.accessibility2`, where
    /// it grows with its label.
    @Suite("Spine seam references")
    @MainActor
    struct SpineSeamReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func seams(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(SeamsFixture(), named: "seams",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        @Test
        func seamsAx2() {
            ReferenceImage.assertMatches(SeamsFixture(), named: "seams",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2))
        }
    }
}

private struct SeamsFixture: View {
    // swiftlint:disable:next type_name
    private typealias F = SpineFixtureWeek

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            DaySeam(date: F.weekStart, isStill: false)
                .padding(.bottom, SpineLayout.dayPadTop - SpineLayout.seamHeight)
            DaySeam(date: LocalDays.adding(4, to: F.weekStart, calendar: F.calendar), isStill: true)
        }
        .background(alignment: .top) { SpineGround() }
        .padding(.top, SpineLayout.feedTop - SpineLayout.seamOverhang)
        .padding(.bottom, 24)
        .background(Color.pebbles.surface)
    }
}
