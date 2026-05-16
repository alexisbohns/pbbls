import SwiftUI

struct ProfileLabCard: View {
    var body: some View {
        NavigationLink {
            LabView()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "lightbulb.max")
                    .font(.title3)
                    .foregroundStyle(Color.pebblesAccent)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Lab")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.pebblesForeground)
                    Text("News & community")
                        .font(.caption)
                        .foregroundStyle(Color.pebblesMutedForeground)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.pebblesMutedForeground)
            }
            .padding(16)
            .background(Color.pebblesListRow)
            .clipShape(RoundedRectangle(cornerRadius: 16))
        }
        .buttonStyle(.plain)
    }
}
