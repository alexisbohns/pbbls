import SwiftUI

/// A day's start (#1130 R8–R10, A1): the hairline with its teeth and the date in
/// the margin, read by VoiceOver as one heading ("Monday, October 5"). The seam
/// is 22 pt with the hairline at 14, so the label hung 14 pt above the hairline
/// has its own room (Android lesson). On a day with moments the label runs on
/// into the day's 58 pt top padding, which follows the seam; a still day sets
/// *still* 14 pt under the hairline and is never shorter than 48 pt below it,
/// nor than its label at the current Dynamic Type size.
struct DaySeam: View {
    let date: Date
    let isStill: Bool
    /// The week's last day keeps its full height to the mouth; any other gives
    /// the next seam's 14 pt overhang back.
    var isLast = true

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.legibilityWeight) private var legibilityWeight

    private var calendar: Calendar { SpineCalendar.current }
    /// Copy reads the app's language; dates add the device's hour cycle and region.
    private var locale: Locale { SpineStrings.appLocale }
    private var dateLocale: Locale { SpineStrings.dateLocale }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            SeamRule()
                .frame(height: SpineLayout.seamOverhang + SpineLayout.seamHeight)
            if isStill {
                Text(verbatim: SpineCopy.still(locale: locale))
                    .spineType(.still)
                    .foregroundStyle(Color.pebbles.outline)
                    .padding(.leading, SpineLayout.colX)
                    .padding(.top, SpineLayout.stillPadTop - SpineLayout.seamHeight)
            }
        }
        .frame(maxWidth: .infinity, minHeight: isStill ? stillHeight : nil, alignment: .topLeading)
        .overlay(alignment: .topLeading) {
            // A zero-width anchor on the line: the label centres on x 40 at any width.
            Color.clear
                .frame(width: 0, height: 0)
                .overlay(alignment: .top) { DayLabel(date: date, locale: dateLocale).fixedSize() }
                .padding(.leading, SpineLayout.spineX)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: SpineDates.longDay(date, calendar: calendar, locale: dateLocale)))
        // A still day says so after its date: the seam is all VoiceOver reaches of it.
        .accessibilityValue(isStill ? SpineCopy.still(locale: locale) : "")
        .accessibilityAddTraits(.isHeader)
    }

    /// 48 pt hairline to hairline (the next seam's overhang comes off it), the
    /// last day's 48 to the mouth, and never less than the label plus 4.
    private var stillHeight: CGFloat {
        let label = Self.labelHeight(dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
        return max(SpineLayout.stillMinHeight + (isLast ? SpineLayout.seamOverhang : 0),
                   label + SpineLayout.labelClearance)
    }

    /// The hung label's height at a Dynamic Type size, from its ranks' resolved metrics:
    /// a still day's floor, and a day with moments' (`SpineLayout.seamPad`).
    static func labelHeight(dynamicTypeSize: DynamicTypeSize, legibilityWeight: LegibilityWeight?) -> CGFloat {
        let numeral = SpineType.resolve(.dayNumeral, dynamicTypeSize: dynamicTypeSize,
                                        legibilityWeight: legibilityWeight)
        let weekday = SpineType.resolve(.dayWeekday, dynamicTypeSize: dynamicTypeSize,
                                        legibilityWeight: legibilityWeight)
        return numeral.uiFont.lineHeight + weekday.uiFont.lineHeight + DayLabel.verticalPadding
    }
}

/// The hairline in `Color.pebbles.seam` at 14 pt, and the teeth under it in
/// `Color.pebbles.teeth`: one 3.5 pt tooth every 7 pt from 2, and a second
/// period, 6.5 pt every 19 pt from 4.
private struct SeamRule: View {
    private struct Teeth {
        let offset: CGFloat
        let period: CGFloat
        let height: CGFloat
    }

    private static let teeth = [Teeth(offset: 2, period: 7, height: 3.5), Teeth(offset: 4, period: 19, height: 6.5)]

    var body: some View {
        Canvas { context, size in
            let top = SpineLayout.seamOverhang
            context.fill(Path(CGRect(x: 0, y: top, width: size.width, height: 1)), with: .color(Color.pebbles.seam))
            var teeth = Path()
            for teethRow in Self.teeth {
                for x in stride(from: teethRow.offset, to: size.width, by: teethRow.period) {
                    teeth.addRect(CGRect(x: x, y: top + 1, width: 1, height: teethRow.height))
                }
            }
            context.fill(teeth, with: .color(Color.pebbles.teeth))
        }
        .accessibilityHidden(true)
    }
}

/// The date in the margin: the numeral over the weekday on a tab of ground,
/// 46 pt wide (wider at accessibility sizes), its bottom corners rounded 10.
private struct DayLabel: View {
    static let verticalPadding = SpineLayout.labelPadTop + SpineLayout.labelGap + SpineLayout.labelPadBottom

    let date: Date
    let locale: Locale

    private var calendar: Calendar { SpineCalendar.current }

    var body: some View {
        VStack(spacing: SpineLayout.labelGap) {
            Text(verbatim: "\(calendar.component(.day, from: date))")
                .spineType(.dayNumeral)
                .foregroundStyle(Color.pebbles.onSurface)
            Text(verbatim: SpineDates.shortWeekday(date, calendar: calendar, locale: locale))
                .spineType(.dayWeekday)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
        }
        .padding(.top, SpineLayout.labelPadTop)
        .padding(.bottom, SpineLayout.labelPadBottom)
        .frame(minWidth: SpineLayout.labelWidth)
        .background(
            UnevenRoundedRectangle(bottomLeadingRadius: SpineLayout.labelRadius,
                                   bottomTrailingRadius: SpineLayout.labelRadius)
                .fill(Color.pebbles.surface)
        )
        .accessibilityHidden(true)
    }
}
