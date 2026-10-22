import SwiftUI

/// The opening (#1130 R18, A1): the kicker, the title in words (the page's
/// heading; balanced by wrapping freely, `lineLimit(nil)`), and the summary.
/// Gaps 46 above, 16 to the title, 12 to the summary.
struct WeekOpeningView: View {
    let text: WeekOpeningText

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(verbatim: text.kicker)
                .spineType(.kicker)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            Text(verbatim: text.title)
                .spineType(.title)
                .foregroundStyle(Color.pebbles.onSurface)
                .multilineTextAlignment(.leading)
                .lineLimit(nil)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, SpineLayout.openingTitleGap)
                .accessibilityAddTraits(.isHeader)
            Text(verbatim: text.summary)
                .spineType(.summary)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .padding(.top, SpineLayout.openingSummaryGap)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, SpineLayout.openingTop)
        .padding(.horizontal, SpineLayout.versoPadX)
    }
}
