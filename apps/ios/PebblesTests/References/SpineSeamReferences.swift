import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1130 D3: Monday's seam with the day's top padding under it, then the
    /// still Friday, on the raked ground with the line: Paper and Night, English
    /// and French (the weekday). At `.accessibility2`, two still days, Thursday
    /// (not the week's last) then Friday (the last): each grows to its label, so
    /// neither label meets the next seam, in the non-last case too.
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
            ReferenceImage.assertMatches(StillDaysFixture(), named: "seams",
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

/// Two still days in a row at accessibility sizes: Thursday gives the next
/// seam's overhang back (`isLast: false`), Friday keeps its full height.
private struct StillDaysFixture: View {
    // swiftlint:disable:next type_name
    private typealias F = SpineFixtureWeek

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            DaySeam(date: LocalDays.adding(3, to: F.weekStart, calendar: F.calendar), isStill: true, isLast: false)
            DaySeam(date: LocalDays.adding(4, to: F.weekStart, calendar: F.calendar), isStill: true)
        }
        .background(alignment: .top) { SpineGround() }
        .padding(.top, SpineLayout.feedTop - SpineLayout.seamOverhang)
        .padding(.bottom, 24)
        .background(Color.pebbles.surface)
    }
}
