import SwiftUI

struct ProfileLabCard: View {
    var body: some View {
        NavigationLink(value: YouRoute.lab) {
            HStack(spacing: Spacing.lg) {
                PbblsIconImage(.sparkle, .tile, style: AnyShapeStyle(Color.pebbles.primary))
                VStack(alignment: .leading) {
                    Text("Lab")
                        .pebblesFont(.headline)
                        .foregroundStyle(Color.pebbles.onSurface)
                    Text("News & community")
                        .pebblesFont(.subhead)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Color.pebbles.outline)
            }
            .contentShape(Rectangle())
            .profileCard()
        }
        .buttonStyle(.plain)
    }
}

#Preview {
    NavigationStack {
        ProfileLabCard().padding()
    }
}
