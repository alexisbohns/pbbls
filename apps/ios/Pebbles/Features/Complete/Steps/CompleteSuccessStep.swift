import SwiftUI

/// The pebble, lit (R12): its derived glyph carved into the stone (fetched
/// after the write, A4; a bare stone when none), its name or "Untitled", and
/// what it earned. The stone's light sweeps across it on arrival, which is
/// this screen's draw-on (it honours Reduce Motion).
struct CompleteSuccessStep: View {
    let name: String?
    let result: CompletionResult
    let valence: Valence
    let emotionId: UUID?
    let onExit: () -> Void

    @Environment(EmotionPaletteService.self) private var palettes

    var body: some View {
        VStack(spacing: Spacing.xl) {
            Spacer(minLength: 0)

            PebbleStoneView(
                valence: valence,
                glyph: result.glyph,
                palette: emotionId.flatMap { palettes.palette(for: $0) },
                followsTilt: true
            )
            .frame(maxWidth: .infinity)
            .frame(height: 280)

            VStack(spacing: Spacing.sm) {
                MomentNameText(name: name, untitledFont: .ysabeauSemiboldItalic(28))
                    .pebblesFont(.title)
                    .multilineTextAlignment(.center)

                if let karma = result.karmaDelta, karma > 0 {
                    HStack(spacing: 6) {
                        Image(systemName: "sparkle")
                            .foregroundStyle(Color.pebbles.primary)
                        Text("+\(karma) karma")
                            .pebblesFont(.headline)
                            .foregroundStyle(Color.pebbles.onSurface)
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
