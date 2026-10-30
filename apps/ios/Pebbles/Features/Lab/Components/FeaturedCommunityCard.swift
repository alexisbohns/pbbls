import SwiftUI

/// Pinned card at the top of the Lab tab inviting users to join the
/// Pebbles WhatsApp community. Static content — the link + description
/// live in `LabConfig`.
struct FeaturedCommunityCard: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                PbblsIconImage(.people, .tile, style: AnyShapeStyle(Color.pebbles.primary))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Join the community")
                        .font(.headline)
                        .foregroundStyle(Color.pebbles.onSurface)
                    Text("Shape Pebbles with other pebblers on WhatsApp.")
                        .font(.footnote)
                        .foregroundStyle(Color.pebbles.onSurfaceVariant)
                }
            }

            HStack(alignment: .center, spacing: 16) {
                Button {
                    openURL(LabConfig.whatsappInviteURL)
                } label: {
                    Text("Open in WhatsApp")
                        .foregroundStyle(Color.pebbles.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .transition(.opacity.combined(with: .move(edge: .bottom)))
            }
        }
        .padding(.vertical, 4)
    }
}

#Preview {
    FeaturedCommunityCard()
        .padding()
}
