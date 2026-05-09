import RiveRuntime
import SwiftUI

/// Header rendered ABOVE each week card on the Path screen: a small Rive
/// cairn animation stacked above the centered "Week N" / "Semaine N"
/// title. Used as the `header:` view of each `Section` so it sits in the
/// gap between cards rather than as a row inside one.
///
/// The week number is read from the supplied `weekStart` Date using the
/// supplied calendar — callers pass the same `Calendar(identifier:
/// .iso8601)` they used to bucket.
///
/// The localized source key is `"Week %lld"`, with `"Semaine %lld"` as
/// the French translation. Xcode auto-extracts the source key on every
/// build because `SWIFT_EMIT_LOC_STRINGS = YES`; the FR value is filled
/// in `Localizable.xcstrings`.
struct WeekSectionHeader: View {
    let weekStart: Date
    let calendar: Calendar

    @State private var cairnViewModel = RiveViewModel(fileName: "pbbls-cairn")

    var body: some View {
        let weekOfYear = calendar.component(.weekOfYear, from: weekStart)
        VStack(spacing: 8) {
            cairnViewModel.view()
                .frame(width: 56, height: 56)
                .accessibilityHidden(true)
            Text("Week \(weekOfYear)")
                .font(.custom("Ysabeau-SemiBold", size: 18))
                .foregroundStyle(Color.pebblesForeground)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 12)
        .textCase(nil) // override the default `.insetGrouped` uppercase header style
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
