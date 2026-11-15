import SwiftUI

/// One format (spec "Tool panel · Ratio"): the format's own shape, hand-drawn
/// and filled, with its ratio written inside. Primary when selected.
struct CropRatioChip: View {
    let ratio: CropRatio
    let isSelected: Bool
    let action: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// The long side of the drawn shape.
    private static let shapeSize: CGFloat = 46
    private static let hitSide: CGFloat = 56
    private static let cornerRadius: CGFloat = 14

    var body: some View {
        Button(action: action) {
            let w = Self.shapeSize * min(ratio.value, 1)
            let h = Self.shapeSize / max(ratio.value, 1)
            WobblyRectangle(seed: ratio.rawValue, cornerRadius: Self.cornerRadius)
                .fill(isSelected ? Color.pebbles.primary : Color.pebbles.surfaceContainerHigh)
                .overlay {
                    // Figures are always Ysabeau, lining and tabular (D22).
                    Text(verbatim: ratio.rawValue)
                        .spineType(.entryTime)
                        .foregroundStyle(isSelected ? Color.pebbles.onPrimary : Color.pebbles.onSurfaceVariant)
                        .lineLimit(1)
                        // The shape is fixed, so its label stops growing before it overflows.
                        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
                }
                .frame(width: w, height: h)
                .frame(width: Self.hitSide, height: Self.hitSide)
                .contentShape(Rectangle())
                .animation(reduceMotion ? nil : .snappy, value: isSelected)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text("\(ratio.width) by \(ratio.height)"))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
    }
}
