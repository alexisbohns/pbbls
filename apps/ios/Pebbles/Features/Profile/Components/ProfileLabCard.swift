import SwiftUI

struct ProfileLabCard: View {
    var body: some View {
        NavigationLink {
            LabView()
        } label: {
            HStack(spacing: 12) {
                Image(systemName: "lightbulb.max")
                    .font(.title3)
                    .foregroundStyle(Color.accent.primary)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Lab")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(Color.system.foreground)
                    Text("News & community")
                        .font(.caption)
                        .foregroundStyle(Color.system.secondary)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Color.system.secondary)
            }
            .padding(16)
            .background(Color.system.background)
            .clipShape(RoundedRectangle(cornerRadius: 16))
            .overlay {
                RoundedRectangle(cornerRadius: 16)
                    .strokeBorder(Color.system.muted, lineWidth: 1)
            }
        }
        .buttonStyle(.plain)
    }
}
