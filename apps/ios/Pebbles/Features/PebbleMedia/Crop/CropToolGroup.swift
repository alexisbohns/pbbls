import SwiftUI

/// The crop editor's tools as a connected button group, single-select (Material
/// 3 Expressive): the buttons sit a hair apart, outer corners are pill ends and
/// inner corners small; the selected button rounds off on every side, takes
/// the tonal fill and the rest of the row's width, and alone shows its label:
/// four labels do not fit side by side. A tool is one more case in `tools`.
struct CropToolGroup: View {
    let tools: [CropTool]
    @Binding var selection: CropTool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private static let height: CGFloat = 48
    private static let gap: CGFloat = 2
    private static let innerRadius: CGFloat = 8
    /// An unselected, icon-only segment: a comfortable target, not a stretched pill.
    private static let iconSegmentWidth: CGFloat = 64

    var body: some View {
        HStack(spacing: Self.gap) {
            ForEach(Array(tools.enumerated()), id: \.element) { index, tool in
                button(tool, index: index)
            }
        }
        .animation(reduceMotion ? nil : .snappy, value: selection)
        .accessibilityElement(children: .contain)
        // Chrome, not content: it stops growing before the labels crowd each other.
        .dynamicTypeSize(...DynamicTypeSize.xxxLarge)
    }

    private func button(_ tool: CropTool, index: Int) -> some View {
        let selected = selection == tool
        let ink = selected ? Color.pebbles.onSecondaryContainer : Color.pebbles.onSurfaceVariant
        return Button { selection = tool } label: {
            HStack(spacing: Spacing.sm) {
                PbblsGlyphView(tool.glyph, size: 22, style: AnyShapeStyle(ink))
                if selected {
                    Text(tool.label)
                        .pebblesFont(.subheadEmphasized)
                        .lineLimit(1)
                        .foregroundStyle(ink)
                        .transition(.opacity)
                }
            }
            .frame(minWidth: Self.iconSegmentWidth, maxWidth: selected ? .infinity : Self.iconSegmentWidth,
                   minHeight: Self.height)
            .background(shape(index: index, selected: selected)
                .fill(selected ? Color.pebbles.secondaryContainer : Color.pebbles.surfaceContainerHigh))
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(tool.label))
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    /// Pill ends on the group's outer corners, small radii between buttons; the
    /// selected one is a pill all round. `UnevenRoundedRectangle` animates each
    /// radius, so the corners morph with the fill.
    private func shape(index: Int, selected: Bool) -> UnevenRoundedRectangle {
        let outer = Self.height / 2
        let leading = selected || index == 0 ? outer : Self.innerRadius
        let trailing = selected || index == tools.count - 1 ? outer : Self.innerRadius
        return UnevenRoundedRectangle(
            topLeadingRadius: leading, bottomLeadingRadius: leading,
            bottomTrailingRadius: trailing, topTrailingRadius: trailing,
            style: .continuous
        )
    }
}

private extension CropTool {
    var glyph: PbblsGlyph {
        switch self {
        case .ratio: .rectangleDashed
        case .rotate: .setSquare
        case .filter: .filterBox
        case .adjust: .slider
        }
    }

    var label: LocalizedStringResource {
        switch self {
        case .ratio: "Reframe"
        case .rotate: "Rotate"
        case .filter: "Filter"
        case .adjust: "Adjust"
        }
    }
}
