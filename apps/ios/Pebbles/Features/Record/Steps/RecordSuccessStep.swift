import SwiftUI

/// Step 10 — the pebble, drawn on.
///
/// Reuses `PebbleReadPetroglyph`, the same component the read view uses: it
/// composites the outline backdrop and traces the composed render with the
/// native draw-on animation, and it already honors Reduce Motion.
///
/// On soft success (`renderSvg` nil) it degrades to name + karma with no
/// artwork rather than blocking — the pebble exists and the user should be
/// told so (D10).
struct RecordSuccessStep: View {
    let name: String
    let response: ComposePebbleResponse
    let valence: Valence
    let emotionId: UUID?
    let onExit: () -> Void

    @Environment(EmotionPaletteService.self) private var palettes

    var body: some View {
        VStack(spacing: Spacing.xl) {
            Spacer(minLength: 0)

            if response.renderSvg != nil {
                PebbleReadPetroglyph(
                    renderSvg: response.renderSvg,
                    renderVersion: response.renderVersion,
                    valence: valence,
                    palette: emotionId.flatMap { palettes.palette(for: $0) }
                )
                .frame(maxWidth: .infinity)
                .frame(height: 280)
            }

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
