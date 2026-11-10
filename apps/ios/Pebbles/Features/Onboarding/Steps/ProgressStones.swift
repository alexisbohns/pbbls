import SwiftUI

/// The stones in the top bar (#1510 §1): passed stones lit, the current one lit and larger, the
/// ones ahead outlined. One element to VoiceOver, "Step n of m".
struct ProgressStones: View {
    let current: Int
    let count: Int

    var body: some View {
        HStack(spacing: 6) {
            ForEach(0..<count, id: \.self) { index in
                stone(index)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text("Step \(current + 1) of \(count)"))
    }

    private func stone(_ index: Int) -> some View {
        let lit = index <= current
        let colour: Color
        if index == current {
            colour = Color.pebbles.primary
        } else {
            colour = lit ? Color.pebbles.onSurfaceVariant : Color.pebbles.outline
        }
        return PbblsGlyphView(.pebbleRound, size: index == current ? 24 : 16,
                              form: lit ? PbblsGlyph.pebbleRound.form(for: .selected) : .regular,
                              style: AnyShapeStyle(colour))
    }
}
