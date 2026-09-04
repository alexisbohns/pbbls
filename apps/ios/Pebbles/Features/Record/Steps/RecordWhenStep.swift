import SwiftUI

/// Step 1 — the moment. Seeded from the photo's EXIF when it had one (D7), so
/// the common case (recording from a picture taken earlier today) needs no
/// input at all beyond confirming.
struct RecordWhenStep: View {
    @Binding var happenedAt: Date
    /// True when the value on screen came from the photo rather than from now.
    let seededFromPhoto: Bool

    var body: some View {
        VStack(spacing: Spacing.md) {
            DatePicker(
                "When",
                selection: $happenedAt,
                displayedComponents: [.date, .hourAndMinute]
            )
            .datePickerStyle(.graphical)
            .labelsHidden()
            .tint(Color.accent.primary)

            if seededFromPhoto {
                Label("Taken from your photo", systemImage: "sparkles")
                    .pebblesFont(.subhead)
                    .foregroundStyle(Color.system.secondary)
            }
        }
    }
}
