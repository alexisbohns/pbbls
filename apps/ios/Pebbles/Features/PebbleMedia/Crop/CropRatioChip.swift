import SwiftUI

/// One format (spec "Tool panel · Ratio"): the shape itself, hand-drawn, no
/// name. Filled when selected.
struct CropRatioChip: View {
    let ratio: CropRatio
    let isSelected: Bool
    let action: () -> Void

    /// The long side of the drawn shape.
    private static let shapeSize: CGFloat = 30

    var body: some View {
        Button(action: action) {
            let w = Self.shapeSize * min(ratio.value, 1)
            let h = Self.shapeSize / max(ratio.value, 1)
            ZStack {
                WobblyRectangle(seed: ratio.rawValue)
                    .fill(isSelected ? Color.pebbles.primaryContainer : Color.clear)
                WobblyRectangle(seed: ratio.rawValue)
                    .stroke(isSelected ? Color.pebbles.primary : Color.pebbles.onSurfaceVariant,
                            style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
            }
            .frame(width: w, height: h)
            .frame(width: 56, height: 56)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("\(ratio.width) by \(ratio.height)"))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
