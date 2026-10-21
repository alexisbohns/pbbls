import SwiftUI

/// Richer row used for announcements in the Lab feed. Shows an optional
/// cover image above the localized title and summary. Tap handling is
/// owned by the parent (typically via a `NavigationLink` to
/// `AnnouncementDetailView`).
struct AnnouncementRow: View {
    let log: Log
    let coverImageURL: URL?

    @Environment(\.locale) private var locale

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let coverImageURL {
                AsyncImage(url: coverImageURL) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    case .empty:
                        Rectangle().fill(Color.pebbles.surfaceContainerHighest)
                    case .failure:
                        Rectangle().fill(Color.pebbles.surfaceContainerHighest)
                    @unknown default:
                        Rectangle().fill(Color.pebbles.surfaceContainerHighest)
                    }
                }
                .frame(height: 140)
                .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
            }

            Text(log.title(for: locale))
                .font(.headline)
                .foregroundStyle(Color.pebbles.onSurface)

            Text(log.summary(for: locale))
                .font(.footnote)
                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                .lineLimit(3)
        }
        .padding(.vertical, 4)
    }
}
