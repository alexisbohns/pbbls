import SwiftUI

/// Tile in the horizontal Collections scroller on the Profile screen.
/// Two visual variants:
///   - `.filled(collection:)` — a real collection: solid border, icon glyph,
///     name + pebble count.
///   - `.empty` — dashed-border placeholder prompting the user to create
///     their first collection.
///
/// Both variants render only their visual content; the parent decides whether
/// to wrap the tile in a `NavigationLink` (filled → detail view) or a
/// `Button` (empty → create sheet). The whole tile is hit-tested via
/// `.contentShape(...)` so taps land regardless of fill.
struct ProfileCollectionCard: View {
    enum Variant {
        case filled(collection: Collection)
        case empty
    }

    let variant: Variant

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.sm) {
            iconBox
            VStack(alignment: .leading, spacing: Spacing.xs) {
                Text(variant.title)
                    .pebblesFont(.headline)
                    .foregroundStyle(Color.system.foreground)
                if let subtitleKey = variant.subtitleKey {
                    Text(subtitleKey)
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.system.secondary)
                }
            }
        }
        .padding(Spacing.lg)
        .frame(width: 140, alignment: .leading)
        .overlay { variant.borderOverlay }
        .contentShape(RoundedRectangle(cornerRadius: Spacing.lg))
    }

    private var iconBox: some View {
        ZStack {
            RoundedRectangle(cornerRadius: Spacing.sm)
                .fill(Color.accent.surface)
            Image(systemName: variant.iconName)
                .pebblesIcon(.small)
                .foregroundStyle(Color.accent.primary)
        }
        .frame(width: Spacing.xxl, height: Spacing.xxl)
    }
}

private extension ProfileCollectionCard.Variant {
    var iconName: String {
        switch self {
        case .filled: return "square.stack.3d.up"
        case .empty:  return "plus"
        }
    }

    var title: LocalizedStringResource {
        switch self {
        case .filled(let collection): return LocalizedStringResource(stringLiteral: collection.name)
        case .empty:                  return "New collection"
        }
    }

    /// `subhead` line under the title. `nil` for the empty tile (no count to show).
    var subtitleKey: LocalizedStringResource? {
        switch self {
        case .filled(let collection):
            // String-catalog plural entry; see Localizable.xcstrings → "%lld pebbles".
            return LocalizedStringResource("\(collection.pebbleCount) pebbles")
        case .empty:
            return nil
        }
    }

    @ViewBuilder
    var borderOverlay: some View {
        switch self {
        case .filled:
            RoundedRectangle(cornerRadius: Spacing.lg)
                .strokeBorder(Color.system.muted, lineWidth: 1)
        case .empty:
            RoundedRectangle(cornerRadius: Spacing.lg)
                .strokeBorder(
                    Color.system.muted,
                    style: StrokeStyle(lineWidth: 1, lineCap: .round, dash: [10, 10])
                )
        }
    }
}

#Preview {
    HStack(spacing: Spacing.sm) {
        ProfileCollectionCard(
            variant: .filled(collection: Collection.preview)
        )
        ProfileCollectionCard(variant: .empty)
    }
    .padding()
}

private extension Collection {
    static var preview: Collection {
        // Workaround: Collection has a custom decoder, no memberwise init.
        // Build via JSON for previews so we keep one source of truth.
        let data = """
        { "id": "11111111-1111-1111-1111-111111111111",
          "name": "Reading list",
          "mode": "pack",
          "pebble_count": [{ "count": 7 }] }
        """.data(using: .utf8)!
        return try! JSONDecoder().decode(Collection.self, from: data)
    }
}
