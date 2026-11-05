import SwiftUI

/// The answers so far under the headline (record-flow D11): "Highlight ·
/// week", then "Highlight · week · Joyful", with Edit back to that step.
struct RecordSummaryPill: View {
    let valence: Valence?
    let emotionName: String?
    let onEdit: () -> Void

    static func text(valence: Valence?, emotionName: String?) -> String? {
        guard let valence else { return nil }
        var parts = [String(localized: valence.shortLabel), String(localized: valence.spanWord)]
        if let emotionName { parts.append(emotionName) }
        return parts.joined(separator: " · ")
    }

    var body: some View {
        if let text = Self.text(valence: valence, emotionName: emotionName) {
            Button(action: onEdit) {
                HStack(spacing: Spacing.xs + 2) {
                    Text(verbatim: text)
                        .pebblesFont(.subheadEmphasized)
                        .lineLimit(2)
                        .multilineTextAlignment(.center)
                    Text("Edit").pebblesFont(.subhead).underline()
                }
                .foregroundStyle(Color.pebbles.onSecondaryContainer)
                .padding(.horizontal, Spacing.md)
                .padding(.vertical, Spacing.xs + 2)
                .frame(minHeight: 32)
                .background(Capsule().fill(Color.pebbles.secondaryContainer))
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(verbatim: text))
            .accessibilityHint(Text("Edit"))
        }
    }
}
