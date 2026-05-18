import SwiftUI

// MARK: - Row position

/// Where a row sits inside its Section, used to mask the border overlay's
/// corner radii. `.only` is the default for single-row sections.
enum PebblesListRowPosition {
    case only
    case top
    case middle
    case bottom
}

func pebblesRowPosition(index: Int, count: Int) -> PebblesListRowPosition {
    if count <= 1 { return .only }
    if index == 0 { return .top }
    if index == count - 1 { return .bottom }
    return .middle
}

// MARK: - List/Form chrome

/// Applied to `List` or `Form`: hides the native grouped background,
/// recolors row separators to `system.muted`, and sets `Spacing.lg`
/// between sections so the bordered groups breathe consistently.
extension View {
    func pebblesList() -> some View {
        modifier(PebblesListModifier())
    }
}

private struct PebblesListModifier: ViewModifier {
    func body(content: Content) -> some View {
        content
            .scrollContentBackground(.hidden)
            .listRowSeparatorTint(Color.system.muted)
            .listSectionSpacing(Spacing.lg)
    }
}

// MARK: - Row chrome

/// Applied to each row inside a Section: replaces the native row background
/// with a `system.muted` stroke that draws only the edges this row owns —
/// top/sides for the first row, sides only for middle rows, bottom/sides for
/// the last. Combined with the list-level `listRowSeparatorTint` (also
/// `system.muted`), the horizontal dividers between rows complete the card.
extension View {
    func pebblesListRow(position: PebblesListRowPosition = .only) -> some View {
        modifier(PebblesListRowModifier(position: position))
    }
}

private struct PebblesListRowModifier: ViewModifier {
    let position: PebblesListRowPosition

    func body(content: Content) -> some View {
        content
            .listRowBackground(borderBackground)
            .listRowSeparator(.hidden)
    }

    @ViewBuilder
    private var borderBackground: some View {
        let radius = Spacing.lg
        switch position {
        case .only:
            RoundedRectangle(cornerRadius: radius)
                .stroke(Color.system.muted, lineWidth: 1)
        case .top:
            UnevenRoundedRectangle(
                cornerRadii: RectangleCornerRadii(
                    topLeading: radius,
                    bottomLeading: 0,
                    bottomTrailing: 0,
                    topTrailing: radius
                )
            )
            .stroke(Color.system.muted, lineWidth: 1)
        case .middle:
            Rectangle()
                .stroke(Color.system.muted, lineWidth: 1)
        case .bottom:
            UnevenRoundedRectangle(
                cornerRadii: RectangleCornerRadii(
                    topLeading: 0,
                    bottomLeading: radius,
                    bottomTrailing: radius,
                    topTrailing: 0
                )
            )
            .stroke(Color.system.muted, lineWidth: 1)
        }
    }
}

// MARK: - Section header

/// Section header typography matching profile cards (Stats, Collections):
/// `.pebblesFont(.cardHeading)` (SF Compact Rounded 15 semibold, uppercase,
/// 10% tracking) in `system.secondary`.
extension Text {
    func pebblesSectionHeader() -> some View {
        self
            .pebblesFont(.cardHeading)
            .foregroundStyle(Color.system.secondary)
    }
}

// MARK: - Preview

#Preview("PebblesList chrome") {
    Form {
        Section {
            Text("Single row")
                .pebblesListRow(position: .only)
        } header: {
            Text("Single").pebblesSectionHeader()
        }

        Section {
            Text("Top row").pebblesListRow(position: .top)
            Text("Middle row").pebblesListRow(position: .middle)
            Text("Bottom row").pebblesListRow(position: .bottom)
        } header: {
            Text("Multi-row").pebblesSectionHeader()
        }
    }
    .pebblesList()
}
