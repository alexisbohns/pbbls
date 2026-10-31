import SwiftUI

/// The three Home shortcuts (spec D3): camera, quick moment, new pebble, on
/// the accent surface (`primaryContainer`, #1382 D11). Settings picks a row or a bento, and plain tiles or pebble
/// shapes (`HomeShortcutsPreference`); the bento's square goes to the shortcut
/// used last.
struct HomeShortcuts: View {
    let onCamera: () -> Void
    let onQuick: () -> Void
    let onPebble: () -> Void
    var cameraDisabled = false

    @AppStorage private var layout: HomeShortcutsLayout
    @AppStorage private var stones: Bool
    @AppStorage private var lastUsed: HomeShortcut

    /// `defaults` lets the references draw each style without touching the device's.
    init(onCamera: @escaping () -> Void, onQuick: @escaping () -> Void, onPebble: @escaping () -> Void,
         cameraDisabled: Bool = false, defaults: UserDefaults = .standard) {
        self.onCamera = onCamera
        self.onQuick = onQuick
        self.onPebble = onPebble
        self.cameraDisabled = cameraDisabled
        _layout = AppStorage(wrappedValue: .row, HomeShortcutsPreference.layoutKey, store: defaults)
        _stones = AppStorage(wrappedValue: true, HomeShortcutsPreference.stonesKey, store: defaults)
        _lastUsed = AppStorage(wrappedValue: .camera, HomeShortcutsPreference.lastUsedKey, store: defaults)
    }

    static let rowMinHeight: CGFloat = 100
    private static let tileRadius: CGFloat = 18

    var body: some View {
        switch layout {
        case .row:
            HStack(spacing: ShortcutStones.gutter) {
                ForEach(HomeShortcut.allCases, id: \.self) { button($0, content: rowContent($0)) }
            }
            .fixedSize(horizontal: false, vertical: true)
            .background {
                fields(HomeShortcut.allCases) {
                    ShortcutStones.rowCells(size: $0)
                } stones: {
                    ShortcutStones.row(size: $0)
                }
            }
        case .bento:
            let order = HomeShortcut.bento(lead: lastUsed)
            ShortcutsBentoLayout {
                button(order[0], content: leadContent(order[0]))
                button(order[1], content: pairContent(order[1]))
                button(order[2], content: pairContent(order[2]))
            }
            .background {
                fields(order) {
                    ShortcutStones.bentoCells(size: $0, leadWidth: ShortcutsBentoLayout.leadWidth(for: $0.width))
                } stones: { size in
                    ShortcutStones.bento(size: size, leadWidth: ShortcutsBentoLayout.leadWidth(for: size.width),
                                         lead: order[0])
                }
            }
        }
    }

    // MARK: - Colour

    /// The accent surface's ink and fill: opaque roles, so they follow Increase Contrast.
    private var ink: Color { Color.pebbles.onPrimaryContainer }
    private var field: Color { Color.pebbles.primaryContainer }

    // MARK: - Fields

    /// One shape per cell, behind the buttons: plain continuous tiles, or the
    /// pieces of one stone.
    private func fields(_ order: [HomeShortcut],
                        cells: @escaping (CGSize) -> [CGRect],
                        stones pieces: @escaping (CGSize) -> [[CGPoint]]) -> some View {
        GeometryReader { proxy in
            let size = proxy.size
            if stones {
                let outlines = pieces(size)
                ForEach(Array(order.enumerated()), id: \.element) { index, shortcut in
                    ShortcutStones.path(outlines[index])
                        .fill(field)
                        .opacity(dimmed(shortcut) ? 0.6 : 1)
                }
            } else {
                let rects = cells(size)
                ForEach(Array(order.enumerated()), id: \.element) { index, shortcut in
                    RoundedRectangle(cornerRadius: Self.tileRadius, style: .continuous)
                        .fill(field)
                        .frame(width: rects[index].width, height: rects[index].height)
                        .offset(x: rects[index].minX, y: rects[index].minY)
                        .opacity(dimmed(shortcut) ? 0.6 : 1)
                }
            }
        }
        .accessibilityHidden(true)
    }

    private func dimmed(_ shortcut: HomeShortcut) -> Bool {
        shortcut == .camera && cameraDisabled
    }

    // MARK: - Buttons

    private func button(_ shortcut: HomeShortcut, content: some View) -> some View {
        Button {
            TapHaptics.play(.selection)
            lastUsed = shortcut
            switch shortcut {
            case .camera: onCamera()
            case .quick: onQuick()
            case .pebble: onPebble()
            }
        } label: {
            content.contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .disabled(dimmed(shortcut))
        .opacity(dimmed(shortcut) ? 0.6 : 1)
        .accessibilityLabel(Text(shortcut.title))
    }

    private func label(_ shortcut: HomeShortcut, alignment: TextAlignment) -> some View {
        Text(shortcut.title)
            .spineType(.actionLabel)
            .foregroundStyle(ink)
            .lineLimit(2)
            .multilineTextAlignment(alignment)
    }

    /// The icon top-left, the label bottom-left (c3). The second and third
    /// cells sit 20 pt in on the leading side, clear of the leaning cut.
    private func rowContent(_ shortcut: HomeShortcut) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            PbblsIconImage(shortcut.icon, .tile, style: AnyShapeStyle(ink))
            Spacer(minLength: 8)
            label(shortcut, alignment: .leading)
        }
        .padding(EdgeInsets(top: 15, leading: shortcut == HomeShortcut.allCases.first ? 16 : 20,
                            bottom: 14, trailing: 12))
        .frame(maxWidth: .infinity, minHeight: Self.rowMinHeight, maxHeight: .infinity, alignment: .leading)
    }

    /// The square's larger icon over its label.
    private func leadContent(_ shortcut: HomeShortcut) -> some View {
        VStack(spacing: 9) {
            PbblsIconImage(shortcut.icon, size: 40, style: AnyShapeStyle(ink))
            label(shortcut, alignment: .center)
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    /// Icon then label, on one left column.
    private func pairContent(_ shortcut: HomeShortcut) -> some View {
        HStack(spacing: 10) {
            PbblsIconImage(shortcut.icon, .tile, style: AnyShapeStyle(ink))
            label(shortcut, alignment: .leading)
            Spacer(minLength: 0)
        }
        .padding(.leading, 18)
        .padding(.trailing, 10)
        .padding(.vertical, 10)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
}

/// The bento: the first subview a square on the left, the other two stacked
/// beside it, together the square's height. Grows past square only when the
/// type is too large for the stacked pair.
struct ShortcutsBentoLayout: Layout {
    private static let gutter = ShortcutStones.gutter

    /// 160 of the 356pt strip the bento was drawn at.
    static func leadWidth(for width: CGFloat) -> CGFloat {
        (width * 0.45).rounded()
    }

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 356
        return CGSize(width: width, height: height(width: width, subviews: subviews))
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        guard subviews.count == 3 else { return }
        let cells = ShortcutStones.bentoCells(size: bounds.size, leadWidth: Self.leadWidth(for: bounds.width))
        for (subview, cell) in zip(subviews, cells) {
            subview.place(at: CGPoint(x: bounds.minX + cell.minX, y: bounds.minY + cell.minY),
                          proposal: ProposedViewSize(cell.size))
        }
    }

    private func height(width: CGFloat, subviews: Subviews) -> CGFloat {
        guard subviews.count == 3 else { return 0 }
        let lead = Self.leadWidth(for: width)
        let pairWidth = width - lead - Self.gutter
        let leadNeeds = subviews[0].sizeThatFits(ProposedViewSize(width: lead, height: nil)).height
        let pairNeeds = max(subviews[1].sizeThatFits(ProposedViewSize(width: pairWidth, height: nil)).height,
                            subviews[2].sizeThatFits(ProposedViewSize(width: pairWidth, height: nil)).height)
        return max(lead, leadNeeds, 2 * pairNeeds + Self.gutter)
    }
}
