import SwiftUI

/// Tile in the horizontal Collections scroller on the Profile screen.
/// Two visual variants — a normal filled tile, and a dashed empty-state
/// tile that prompts the user to create their first collection.
struct ProfileCollectionCard: View {
    enum Variant {
        case filled(name: String)
        case empty
    }

    let variant: Variant
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: 8) {
                Image(systemName: variant.iconName)
                    .font(.title3)
                    .foregroundStyle(variant.iconColor)
                Spacer(minLength: 0)
                Text(variant.title)
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(variant.textColor)
                    .lineLimit(2)
            }
            .padding(12)
            .frame(width: 140, height: 120, alignment: .leading)
            .background(variant.backgroundColor)
            .overlay { variant.borderOverlay }
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}

private extension ProfileCollectionCard.Variant {
    var iconName: String {
        switch self {
        case .filled: return "square.stack.3d.up"
        case .empty:  return "plus"
        }
    }
    var iconColor: Color {
        switch self {
        case .filled: return .pebblesAccent
        case .empty:  return .pebblesMutedForeground
        }
    }
    var title: LocalizedStringResource {
        switch self {
        case .filled(let name): return LocalizedStringResource(stringLiteral: name)
        case .empty:            return "New collection"
        }
    }
    var textColor: Color {
        switch self {
        case .filled: return .pebblesForeground
        case .empty:  return .pebblesMutedForeground
        }
    }
    var backgroundColor: Color {
        switch self {
        case .filled: return .pebblesListRow
        case .empty:  return .clear
        }
    }
    @ViewBuilder
    var borderOverlay: some View {
        switch self {
        case .filled:
            EmptyView()
        case .empty:
            RoundedRectangle(cornerRadius: 16)
                .strokeBorder(
                    Color.pebblesMutedForeground,
                    style: StrokeStyle(lineWidth: 1.5, dash: [4])
                )
        }
    }
}
