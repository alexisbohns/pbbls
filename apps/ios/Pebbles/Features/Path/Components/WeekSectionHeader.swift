import SwiftUI

/// Centered "Week N" title rendered as the first list row inside each
/// week card on the Path screen. The week number is read from the
/// supplied `weekStart` Date using the supplied calendar — callers pass
/// the same `Calendar(identifier: .iso8601)` they used to bucket.
///
/// The localized source key is `"Week %lld"`, with `"Semaine %lld"` as
/// the French translation. Xcode auto-extracts the source key on every
/// build because `SWIFT_EMIT_LOC_STRINGS = YES`; the FR value is filled
/// in `Localizable.xcstrings`.
struct WeekSectionHeader: View {
    let weekStart: Date
    let calendar: Calendar

    var body: some View {
        let weekOfYear = calendar.component(.weekOfYear, from: weekStart)
        Text("Week \(weekOfYear)")
            .font(.custom("Ysabeau-SemiBold", size: 18))
            .foregroundStyle(Color.pebblesForeground)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
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
            WeekSectionHeader(
                weekStart: previewWeekStart(),
                calendar: previewISOCalendar()
            )
            .listRowBackground(Color.pebblesListRow)
        }
    }
}
