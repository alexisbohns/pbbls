import SwiftUI

/// "Two questions, one stone." (D8): opens once over the first valence step of
/// an account with no completed pebble, then lives behind "(i) How to read
/// this". An annotated fan with the chosen stone lit, four lines, Got it.
struct ValenceHelpSheet: View {
    /// The reference render cannot draw a `ScrollView`; the app always scrolls.
    var scrolls = true

    @Environment(\.dismiss) private var dismiss

    /// The fan drawn at this fraction of its reference canvas: small enough
    /// that the fan, the four lines and Got it fit the medium detent.
    private static let fanScale: CGFloat = 0.62

    var body: some View {
        Group {
            if scrolls {
                ScrollView { content }
            } else {
                content
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Color.pebbles.surface)
    }

    private var content: some View {
        VStack(spacing: Spacing.md) {
            ValencePickerContent(selected: .highlightMedium, onSelect: { _ in }, showsCaption: false)
                .allowsHitTesting(false)
                .scaleEffect(Self.fanScale, anchor: .top)
                .frame(
                    width: ValenceFanLayout.reference.width * Self.fanScale,
                    height: (ValenceFanLayout.reference.height + ValencePickerContent.columnLabelTail) * Self.fanScale,
                    alignment: .top
                )
                .clipped()
            VStack(alignment: .leading, spacing: Spacing.xs) {
                line("Two questions, one stone.")
                line("Left to right: lowlight, neutral, highlight.")
                line("Bottom to top: did it fill your day, your week, your month? Bigger moment, bigger stone.")
                line("No wrong stone. You can change it later.")
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button("Got it") { dismiss() }
                .buttonStyle(PebblesPrimaryButtonStyle())
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.vertical, Spacing.sm)
    }

    private func line(_ text: LocalizedStringResource) -> some View {
        Text(text)
            .pebblesFont(.subhead)
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
