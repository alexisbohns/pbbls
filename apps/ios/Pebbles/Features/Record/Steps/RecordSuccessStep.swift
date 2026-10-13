import SwiftUI

/// Step 10 — the pebble's name and karma.
///
/// The compose response no longer carries a render, so this old flow shows
/// no artwork (it is deleted in #1047's last part; its replacement draws the
/// stone from the pebble's derived glyph).
struct RecordSuccessStep: View {
    let name: String
    let response: ComposePebbleResponse
    let onExit: () -> Void

    var body: some View {
        VStack(spacing: Spacing.xl) {
            Spacer(minLength: 0)

            VStack(spacing: Spacing.sm) {
                // User-authored, so never localized.
                Text(verbatim: name)
                    .pebblesFont(.title)
                    .foregroundStyle(Color.system.foreground)
                    .multilineTextAlignment(.center)

                if let karma = response.karmaDelta, karma > 0 {
                    HStack(spacing: 6) {
                        Image(systemName: "sparkle")
                            .foregroundStyle(Color.accent.primary)
                        Text("+\(karma) karma")
                            .pebblesFont(.headline)
                            .foregroundStyle(Color.system.foreground)
                    }
                    .accessibilityElement(children: .combine)
                    .accessibilityLabel(Text("Earned \(karma) karma"))
                }
            }

            Spacer(minLength: 0)

            Button("Back to my path", action: onExit)
                .buttonStyle(PebblesPrimaryButtonStyle())
        }
        .padding(.horizontal, Spacing.lg)
        .padding(.bottom, Spacing.lg)
    }
}
