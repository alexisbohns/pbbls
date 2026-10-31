import SwiftUI

/// A row that opens something (#1382 D14, D19): a chip stone holding a mark,
/// the title in `rowTitle` (or `rowTitleDone`), its line in `rowLine`, and a
/// chevron while there is somewhere to go. One combined element; the caller
/// wraps it in a `NavigationLink` or a `Button`. Used by Home's About list and
/// You's Glyphs.
struct PebblesLinkRow: View {
    enum Chip {
        /// A Pebbles icon at 26 in `primary`.
        case icon(PbblsIcon)
        /// Any mark the data chooses (a guide's own illustration).
        case view(AnyView)
    }

    let title: String
    let line: String
    let chip: Chip
    var done = false

    static let chipSize = CGSize(width: 48, height: 46)

    var body: some View {
        HStack(alignment: .center, spacing: 14) {
            chipMark
                .frame(width: Self.chipSize.width, height: Self.chipSize.height)
                .background {
                    StoneShape(seed: "chip-" + title, options: StoneOptions(corner: 16))
                        .fill(Color.pebbles.surfaceContainer)
                        .accessibilityHidden(true)
                }
            VStack(alignment: .leading, spacing: 2) {
                Text(verbatim: title)
                    .spineType(done ? .rowTitleDone : .rowTitle)
                    .foregroundStyle(done ? Color.pebbles.onSurfaceVariant : Color.pebbles.onSurface)
                Text(verbatim: line)
                    .spineType(.rowLine)
                    .foregroundStyle(done ? Color.pebbles.outline : Color.pebbles.onSurfaceVariant)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if !done {
                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.pebbles.outline)
                    .accessibilityHidden(true)
            }
        }
        .padding(.vertical, 10)
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private var chipMark: some View {
        switch chip {
        case .icon(let icon):
            PbblsIconImage(icon, size: 26, style: AnyShapeStyle(Color.pebbles.primary))
        case .view(let view):
            view.accessibilityHidden(true)
        }
    }
}
