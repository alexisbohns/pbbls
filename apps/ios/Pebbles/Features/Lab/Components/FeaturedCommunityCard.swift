import SwiftUI

/// Pinned card at the top of the Lab tab inviting users to join the
/// Pebbles WhatsApp community. Static content — the link + description
/// live in `LabConfig`.
struct FeaturedCommunityCard: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 12) {
                Image(systemName: "bubble.left.and.bubble.right.fill")
                    .font(.title2)
                    .foregroundStyle(Color.pebblesAccent)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Join the community")
                        .font(.headline)
                        .foregroundStyle(Color.pebblesForeground)
                    Text("Shape Pebbles with other pebblers on WhatsApp.")
                        .font(.footnote)
                        .foregroundStyle(Color.pebblesMutedForeground)
                }
            }

            HStack(alignment: .center, spacing: 16) {
                Button {
                    openURL(LabConfig.whatsappInviteURL)
                } label: {
                    Text("Open in WhatsApp")
                        .foregroundStyle(.white)
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
