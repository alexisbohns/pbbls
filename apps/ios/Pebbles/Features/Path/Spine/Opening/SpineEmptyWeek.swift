import SwiftUI

/// The current week with nothing in it yet (#1130): "Fresh week", the call to
/// collect one and the button that opens Capture. A copy of the old row
/// Path's empty block with the same catalogue keys; D5b removed the original.
struct SpineEmptyWeek: View {
    let onCreate: () -> Void

    var body: some View {
        VStack(spacing: 12) {
            Text("Fresh week")
                .spineType(.bareTitle)
                .foregroundStyle(Color.pebbles.onSurface)
            Text("Collect a new pebble")
                .spineType(.summary)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
            Button(action: onCreate) {
                Image(systemName: "plus")
                    .font(.title2.weight(.semibold))
                    .foregroundStyle(Color.pebbles.onPrimaryContainer)
                    .frame(width: SpineLayout.minTarget + 12, height: SpineLayout.minTarget + 12)
                    .background(RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .fill(Color.pebbles.primaryContainer))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text("New pebble"))
            .padding(.top, 12)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, SpineLayout.emptyPadY)
    }
}
