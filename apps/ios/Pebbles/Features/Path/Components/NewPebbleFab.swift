import SwiftUI

/// Path's floating "New pebble" button (#903 §5), trailing on the FAB row.
/// A tap opens Capture. There is no long-press: none existed on iOS, and
/// Android's went with the all-at-once form (§1).
struct NewPebbleFab: View {
    let onTap: () -> Void

    private static let side: CGFloat = 56
    private static let cornerRadius: CGFloat = 16

    var body: some View {
        Button(action: onTap) {
            Image(systemName: "plus")
                .font(.title2.weight(.semibold))
                .foregroundStyle(Color.pebbles.onPrimaryContainer)
                .frame(width: Self.side, height: Self.side)
                .background(
                    RoundedRectangle(cornerRadius: Self.cornerRadius, style: .continuous)
                        .fill(Color.pebbles.primaryContainer)
                )
        }
        .buttonStyle(.plain)
        .accessibilityLabel("New pebble")
    }
}

#Preview {
    NewPebbleFab(onTap: {}).padding()
}
