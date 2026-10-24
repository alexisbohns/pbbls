import SwiftUI

/// A month's page before its wrap has loaded (#1226): its year and its name,
/// the name the page's heading. Android: `MonthPagePlaceholder`.
struct MonthPagePlaceholder: View {
    let month: CalendarMonth

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(verbatim: String(month.year))
                .spineType(.kicker)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            Text(verbatim: WrapCopy.monthName(month, locale: SpineStrings.appLocale))
                .spineType(.title)
                .foregroundStyle(Color.pebbles.onSurface)
                .accessibilityAddTraits(.isHeader)
        }
        .padding(.horizontal, SpineLayout.versoPadX)
        .padding(.top, 64)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}
