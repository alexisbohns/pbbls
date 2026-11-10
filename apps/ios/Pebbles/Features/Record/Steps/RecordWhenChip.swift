import SwiftUI

/// The When step as a chip (record-flow D4): "Today, 21:10 ▾" or "From your
/// photo · Sun 3 Nov ▾", opening the graphical calendar in a sheet. The EXIF
/// seed and its label stay; a user edit always wins.
struct RecordWhenChip: View {
    @Binding var happenedAt: Date
    let source: CaptureFlowModel.WhenSource

    @State private var isPresented = false

    private var locale: Locale { SpineStrings.appLocale }

    var body: some View {
        Button { isPresented = true } label: {
            HStack(spacing: Spacing.xs + 2) {
                PbblsGlyphView(source == .photo ? .sparkle : .calendarDay, size: 16, relativeTo: .subheadline,
                               style: AnyShapeStyle(Color.pebbles.onSecondaryContainer))
                Text(verbatim: label)
                    .pebblesFont(.subheadEmphasized)
                    .foregroundStyle(Color.pebbles.onSecondaryContainer)
                    .lineLimit(1)
                Image(systemName: "chevron.down")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.pebbles.onSecondaryContainer)
            }
            .padding(.horizontal, Spacing.md)
            .frame(height: 36)
            .background(Capsule().fill(Color.pebbles.secondaryContainer))
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("When"))
        .accessibilityValue(Text(verbatim: label))
        .sheet(isPresented: $isPresented) {
            NavigationStack {
                DatePicker("When", selection: $happenedAt, displayedComponents: [.date, .hourAndMinute])
                    .datePickerStyle(.graphical)
                    .labelsHidden()
                    .tint(Color.pebbles.primary)
                    .padding(Spacing.lg)
                    .pebblesToolbarTitle("When did it happen?")
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) {
                            PebbleToolbarButton("Done") { isPresented = false }
                        }
                    }
                    .pebblesScreen()
            }
            .presentationDetents([.medium, .large])
        }
    }

    /// Today keeps the time; another day shows the weekday and date.
    private var label: String {
        let calendar = SpineCalendar.current
        let day: String
        if calendar.isDateInToday(happenedAt) {
            day = String(localized: "Today")
        } else if calendar.isDateInYesterday(happenedAt) {
            day = String(localized: "Yesterday")
        } else {
            day = happenedAt.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated).locale(locale))
        }
        let time = happenedAt.formatted(.dateTime.hour().minute().locale(locale))
        let when = "\(day), \(time)"
        return source == .photo ? String(localized: "From your photo · \(when)") : when
    }
}
