import RiveRuntime
import SwiftUI
import UIKit

/// Header rendered ABOVE each week card on the Path screen: a small Rive
/// cairn animation stacked above the uppercased, tracked "WEEK N" /
/// "SEMAINE N" title. Used as the `header:` view of each `Section` so it
/// sits in the gap between cards rather than as a row inside one.
///
/// The week number is read from the supplied `weekStart` Date using the
/// supplied calendar — callers pass the same `Calendar(identifier:
/// .iso8601)` they used to bucket.
///
/// The localized source key is `"Week %lld"`, with `"Semaine %lld"` as
/// the French translation. Xcode auto-extracts the source key on every
/// build because `SWIFT_EMIT_LOC_STRINGS = YES`; the FR value is filled
/// in `Localizable.xcstrings`. Uppercasing happens visually via
/// `.textCase(.uppercase)`.
struct WeekSectionHeader: View {
    let weekStart: Date
    let calendar: Calendar
    /// When false, the title text is faded out — used by `PathView` to
    /// gate the first week's title behind its cairn animation. Other
    /// weeks pass `true` and the title shows immediately.
    var titleVisible: Bool = true
    /// Invoked the first time the cairn animation reaches its stopped
    /// state. `PathView` passes a non-nil closure for the first week
    /// only and uses it to start the per-row reveal cascade.
    var onCairnFinished: (() -> Void)? = nil

    @State private var cairnViewModel = CairnAnimationViewModel(fileName: "pbbls-cairn")

    private static let titleSize: CGFloat = 14

    /// Ysabeau-SemiBold with OpenType proportional + lining figures applied
    /// so the digits in "WEEK 19" align to cap height with proportional
    /// (not tabular) widths. Matches the design spec.
    ///
    /// Feature constants from `CoreText/SFNTLayoutTypes.h`:
    /// - Number Spacing (type 6) → Proportional Numbers (selector 1)
    /// - Number Case (type 21) → Upper Case Numbers / lining (selector 1)
    private static var titleFont: SwiftUI.Font {
        let descriptor = UIFontDescriptor(name: "Ysabeau-SemiBold", size: titleSize)
            .addingAttributes([
                .featureSettings: [
                    [
                        UIFontDescriptor.FeatureKey.type: 6,
                        UIFontDescriptor.FeatureKey.selector: 1,
                    ],
                    [
                        UIFontDescriptor.FeatureKey.type: 21,
                        UIFontDescriptor.FeatureKey.selector: 1,
                    ],
                ],
            ])
        return SwiftUI.Font(UIFont(descriptor: descriptor, size: titleSize))
    }

    var body: some View {
        let weekOfYear = calendar.component(.weekOfYear, from: weekStart)
        VStack(spacing: 0) {
            cairnViewModel.view()
                .frame(width: 56, height: 56)
                .accessibilityHidden(true)
                .onAppear {
                    cairnViewModel.onStopped = onCairnFinished
                }
            Text("Week \(weekOfYear)")
                .font(Self.titleFont)
                .tracking(2.5)
                .textCase(.uppercase)
                .foregroundStyle(Color.pebblesMutedForeground)
                .opacity(titleVisible ? 1 : 0)
                .animation(.easeOut(duration: 0.25), value: titleVisible)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
    }
}

private func previewWeekStart() -> Date {
    var iso = Calendar(identifier: .iso8601)
    iso.timeZone = TimeZone(identifier: "UTC")!
    var comps = DateComponents()
    comps.timeZone = TimeZone(identifier: "UTC")
    comps.yearForWeekOfYear = 2026
    comps.weekOfYear = 19
    comps.weekday = 2
    return iso.date(from: comps) ?? Date()
}

private func previewISOCalendar() -> Calendar {
    var iso = Calendar(identifier: .iso8601)
    iso.timeZone = TimeZone(identifier: "UTC")!
    return iso
}

#Preview {
    List {
        Section {
            Text("Pebble row")
                .listRowBackground(Color.pebblesListRow)
        } header: {
            WeekSectionHeader(
                weekStart: previewWeekStart(),
                calendar: previewISOCalendar()
            )
        }
    }
}
