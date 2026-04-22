import SwiftUI
import CoreImage.CIFilterBuiltins
import UIKit

/// Pinned card at the top of the Lab tab inviting users to join the
/// Pebbles WhatsApp community. Static content — the link + description
/// live in `LabConfig`, the QR is generated client-side from the URL.
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

    private func qrImage(for url: URL) -> UIImage? {
        let filter = CIFilter.qrCodeGenerator()
        filter.message = Data(url.absoluteString.utf8)
        filter.correctionLevel = "M"
        guard let ciImage = filter.outputImage else { return nil }
        // Scale up so the QR is crisp at the rendered size.
        let scaled = ciImage.transformed(by: CGAffineTransform(scaleX: 8, y: 8))
        let context = CIContext()
        guard let cgImage = context.createCGImage(scaled, from: scaled.extent) else { return nil }
        return UIImage(cgImage: cgImage)
    }
}

#Preview {
    FeaturedCommunityCard()
        .padding()
}
