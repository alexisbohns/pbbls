import SwiftUI
import Supabase
import os

/// Form row rendering an already-saved snap (loaded from the DB) inside the
/// edit-pebble photo section. Layout matches `AttachedPhotoView` (56×56
/// thumbnail + label + trailing button) so the section feels consistent
/// regardless of whether the snap is `.existing` or `.pending`.
///
/// Stateless besides the lazily-loaded thumb URL. The remove button calls
/// back into the parent (`EditPebbleSheet`) which owns the eager
/// `delete_pebble_media` RPC + Storage cleanup.
struct ExistingSnapRow: View {
    let storagePath: String
    let isRemoving: Bool
    let onRemove: () -> Void

    @Environment(SupabaseService.self) private var supabase
    @State private var thumbURL: URL?
    @State private var loadFailed = false

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "existing-snap-row")

    var body: some View {
        HStack(spacing: 12) {
            thumbnail
            VStack(alignment: .leading, spacing: 4) {
                Text("Photo")
                    .font(.subheadline)
                Label("Saved", systemImage: "checkmark.circle.fill")
                    .labelStyle(.titleAndIcon)
                    .font(.caption)
                    .foregroundStyle(.green)
            }
            Spacer()
            trailingButton
        }
        .task { await loadThumbURL() }
    }

    @ViewBuilder
    private var thumbnail: some View {
        if let thumbURL {
            AsyncImage(url: thumbURL) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .scaledToFill()
                        .frame(width: 56, height: 56)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                case .empty:
                    placeholder
                case .failure:
                    placeholder
                @unknown default:
                    placeholder
                }
            }
        } else {
            placeholder
        }
    }

    private var placeholder: some View {
        RoundedRectangle(cornerRadius: 8)
            .fill(Color.secondary.opacity(0.2))
            .frame(width: 56, height: 56)
    }

    @ViewBuilder
    private var trailingButton: some View {
        if isRemoving {
            ProgressView()
        } else {
            Button(role: .destructive, action: onRemove) {
                Image(systemName: "xmark.circle.fill")
                    .foregroundStyle(.secondary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Remove photo")
        }
    }

    private func loadThumbURL() async {
        do {
            let urls = try await PebbleSnapRepository(client: supabase.client)
                .signedURLs(storagePrefix: storagePath)
            self.thumbURL = urls.thumb
        } catch {
            Self.logger.warning(
                "thumb URL fetch failed: \(error.localizedDescription, privacy: .private)"
            )
            self.loadFailed = true
        }
    }
}
