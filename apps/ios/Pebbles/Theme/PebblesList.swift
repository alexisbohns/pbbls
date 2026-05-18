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
        content.listRowBackground(
            PebblesSectionBorderShape(position: position, radius: Spacing.lg)
                .stroke(Color.system.muted, lineWidth: 1)
        )
    }
}

/// Draws only the perimeter edges this row owns inside the section card.
/// Open paths (top/middle/bottom) avoid double-stroking shared horizontal
/// edges — the system row separator handles those dividers.
private struct PebblesSectionBorderShape: Shape {
    let position: PebblesListRowPosition
    let radius: CGFloat

    func path(in rect: CGRect) -> Path {
        // Inset by half the line width so the 1pt stroke stays fully inside
        // the row's clip bounds (otherwise the outer edge gets clipped).
        let bounds = rect.insetBy(dx: 0.5, dy: 0.5)
        let cornerR = min(radius, min(bounds.width, bounds.height) / 2)
        var path = Path()
        switch position {
        case .only:
            path.addRoundedRect(
                in: bounds,
                cornerSize: CGSize(width: cornerR, height: cornerR)
            )
        case .top:
            path.move(to: CGPoint(x: bounds.minX, y: bounds.maxY))
            path.addLine(to: CGPoint(x: bounds.minX, y: bounds.minY + cornerR))
            path.addArc(
                center: CGPoint(x: bounds.minX + cornerR, y: bounds.minY + cornerR),
                radius: cornerR,
                startAngle: .degrees(180),
                endAngle: .degrees(270),
                clockwise: false
            )
            path.addLine(to: CGPoint(x: bounds.maxX - cornerR, y: bounds.minY))
            path.addArc(
                center: CGPoint(x: bounds.maxX - cornerR, y: bounds.minY + cornerR),
                radius: cornerR,
                startAngle: .degrees(270),
                endAngle: .degrees(0),
                clockwise: false
            )
            path.addLine(to: CGPoint(x: bounds.maxX, y: bounds.maxY))
        case .middle:
            path.move(to: CGPoint(x: bounds.minX, y: bounds.minY))
            path.addLine(to: CGPoint(x: bounds.minX, y: bounds.maxY))
            path.move(to: CGPoint(x: bounds.maxX, y: bounds.minY))
            path.addLine(to: CGPoint(x: bounds.maxX, y: bounds.maxY))
        case .bottom:
            path.move(to: CGPoint(x: bounds.minX, y: bounds.minY))
            path.addLine(to: CGPoint(x: bounds.minX, y: bounds.maxY - cornerR))
            path.addArc(
                center: CGPoint(x: bounds.minX + cornerR, y: bounds.maxY - cornerR),
                radius: cornerR,
                startAngle: .degrees(180),
                endAngle: .degrees(90),
                clockwise: true
            )
            path.addLine(to: CGPoint(x: bounds.maxX - cornerR, y: bounds.maxY))
            path.addArc(
                center: CGPoint(x: bounds.maxX - cornerR, y: bounds.maxY - cornerR),
                radius: cornerR,
                startAngle: .degrees(90),
                endAngle: .degrees(0),
                clockwise: true
            )
            path.addLine(to: CGPoint(x: bounds.maxX, y: bounds.minY))
        }
        return path
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
