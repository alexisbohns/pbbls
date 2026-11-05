import SwiftUI

/// "Two questions, one stone." (D8): opens once over the first valence step of
/// an account with no completed pebble, then lives behind "(i) How to read
/// this". An annotated fan with the chosen stone lit, four lines, Got it.
struct ValenceHelpSheet: View {
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(spacing: Spacing.lg) {
            ValencePickerContent(selected: .highlightMedium, onSelect: { _ in })
                .allowsHitTesting(false)
                .scaleEffect(0.8)
                .frame(height: 300)
            VStack(alignment: .leading, spacing: Spacing.sm) {
                line("Two questions, one stone.")
                line("Left to right: lowlight, neutral, highlight.")
                line("Bottom to top: did it fill your day, your week, your month? Bigger moment, bigger stone.")
                line("No wrong stone. You can change it later.")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button("Got it") { dismiss() }
                .buttonStyle(PebblesPrimaryButtonStyle())
        }
        .padding(Spacing.lg)
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Color.pebbles.surface)
    }

    private func line(_ text: LocalizedStringResource) -> some View {
        Text(text)
            .pebblesFont(.body)
            .foregroundStyle(Color.pebbles.onSurface)
            .fixedSize(horizontal: false, vertical: true)
    }
}

/// The per-account seen flag, so a back-out never reopens the sheet.
enum ValenceHelpSeen {
    static func key(_ owner: UUID) -> String { "valenceHelpSeen.\(owner.uuidString.lowercased())" }
    static func seen(_ owner: UUID) -> Bool { UserDefaults.standard.bool(forKey: key(owner)) }
    static func markSeen(_ owner: UUID) { UserDefaults.standard.set(true, forKey: key(owner)) }
}

#Preview {
    ValenceHelpSheet()
}
