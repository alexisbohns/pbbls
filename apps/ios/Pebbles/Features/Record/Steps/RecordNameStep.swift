import SwiftUI

/// Step 2 — what to call it. Clamped to 40 characters on the way in, so the
/// counter can never show an over-limit value and there is no error state to
/// design (D3). The limit is front-end only.
struct RecordNameStep: View {
    let name: String
    let limit: Int
    let onChange: (String) -> Void

    @FocusState private var isFocused: Bool

    var body: some View {
        VStack(alignment: .trailing, spacing: Spacing.sm) {
            TextField(
                "Name",
                text: Binding(get: { name }, set: onChange),
                axis: .vertical
            )
            .lineLimit(1...3)
            .pebblesFont(.title)
            .multilineTextAlignment(.center)
            .focused($isFocused)
            .submitLabel(.done)
            .padding(Spacing.md)
            .background(Color.system.muted)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            Text(verbatim: "\(name.count)/\(limit)")
                .pebblesFont(.subhead)
                .foregroundStyle(
                    name.count == limit ? Color.accent.primary : Color.system.secondary
                )
                .accessibilityLabel(Text("\(name.count) of \(limit) characters"))
        }
        .onAppear { isFocused = true }
    }
}
