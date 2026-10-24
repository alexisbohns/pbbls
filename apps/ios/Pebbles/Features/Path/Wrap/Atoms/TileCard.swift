import SwiftUI

/// A wrap tile (#1226): 128 pt wide and at least 112 tall, in a rule-coloured
/// continuous rounded border, dashed for a tile that invites rather than
/// reports (the live week, nobody named yet). Dynamic Type grows its height,
/// never its width (D11); in a row, `.fixedSize(horizontal: false, vertical: true)`
/// gives every tile the tallest one's height. With an action it is a button, written as `Button`
/// is (`TileCard { open() } content: { … }`); the caller gives the tile its
/// accessibility label.
struct TileCard<Content: View>: View {
    let dashed: Bool
    let action: (() -> Void)?
    let content: Content

    init(dashed: Bool = false, action: @escaping () -> Void, @ViewBuilder content: () -> Content) {
        self.dashed = dashed
        self.action = action
        self.content = content()
    }

    /// A tile that only shows.
    init(dashed: Bool = false, @ViewBuilder content: () -> Content) {
        self.dashed = dashed
        action = nil
        self.content = content()
    }

    var body: some View {
        if let action {
            Button(action: action) { card }
                .buttonStyle(.plain)
        } else {
            card
        }
    }

    private var card: some View {
        let shape = RoundedRectangle(cornerRadius: SpineLayout.tileCorner, style: .continuous)
        return VStack(alignment: .leading, spacing: 0) { content }
            .padding(SpineLayout.tilePad)
            .frame(width: SpineLayout.tileWidth, alignment: .topLeading)
            // Fills the height it is offered, so a row that fixes its height lines its tiles up.
            .frame(minHeight: SpineLayout.tileMinHeight, maxHeight: .infinity, alignment: .topLeading)
            .overlay {
                shape
                    .inset(by: SpineLayout.rule / 2)
                    .stroke(Color.pebbles.rule,
                            style: StrokeStyle(lineWidth: SpineLayout.rule, dash: dashed ? [4, 3] : []))
            }
            .contentShape(shape)
    }
}

/// A tile's small-caps label: one line, shrunk rather than cut when a large
/// text size outgrows the tile's fixed width.
struct TileHeader: View {
    let label: String

    var body: some View {
        Text(verbatim: label)
            .spineType(.sectionLabel)
            .foregroundStyle(Color.pebbles.onSurfaceVariant)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
    }
}
