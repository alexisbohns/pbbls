import SwiftUI

/// The week's one line under the strip (#1226, D3): `WEEK 41  Eleven pebbles,
/// five people.` The week number is said once, here, as the page's heading; the
/// summary is `WrapCopy.line`, or nothing until the wrap first loads. An empty
/// current week offers its way to a first pebble. Android: `WeekLine`.
struct WeekLine: View {
    let isoWeek: Int
    let summary: String?
    var onCollect: (() -> Void)?

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // At accessibility sizes the summary goes under the number rather than beside it.
            if dynamicTypeSize.isAccessibilitySize {
                VStack(alignment: .leading, spacing: 4) {
                    number
                    summaryText
                }
            } else {
                HStack(alignment: .firstTextBaseline, spacing: 10) {
                    number
                    summaryText
                }
            }
            if let onCollect {
                Button(action: onCollect) {
                    Text(verbatim: WrapCopy.collect(locale: locale))
                        .spineType(.mouthLine)
                        .underline()
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                        .frame(minHeight: SpineLayout.minTarget)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, SpineLayout.versoPadX)
        .padding(.top, 12)
    }

    private var number: some View {
        Text(verbatim: WrapCopy.week(isoWeek, locale: locale))
            .spineType(.masthead)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .fixedSize()
            .accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder
    private var summaryText: some View {
        if let summary {
            Text(verbatim: summary)
                .spineType(.mouthLine)
                .foregroundStyle(Color.pebbles.onSurface)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}
