import SwiftUI

/// Lazy-loads signed URLs for a single snap and renders the original (1024 px)
/// JPEG. Designed for the pebble detail sheet — caller passes the
/// `storage_path` from `public.snaps`, no auth/user knowledge required.
///
/// The view does not clip its output: callers decide framing and corner
/// radius. Pass `contentMode: .fill` for cover-style banners and `.fit` for
/// natural-aspect previews.
struct SnapImageView: View {
    let storagePath: String
    var contentMode: ContentMode = .fit

    @Environment(SupabaseService.self) private var supabase

    @State private var urls: PebbleSnapRepository.SignedURLs?
    @State private var loadError = false

    var body: some View {
        Group {
            if let urls {
                AsyncImage(url: urls.original) { phase in
                    switch phase {
                    case .empty:
                        ProgressView()
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    case .failure:
                        fallbackPlaceholder
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: contentMode)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    @unknown default:
                        fallbackPlaceholder
                    }
                }
            } else if loadError {
                fallbackPlaceholder
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .task {
            do {
                urls = try await PebbleSnapRepository(client: supabase.client)
                    .signedURLs(storagePrefix: storagePath)
            } catch {
                loadError = true
            }
        }
    }

    private var fallbackPlaceholder: some View {
        Rectangle()
            .fill(Color.secondary.opacity(0.1))
            .overlay(
                Image(systemName: "photo.on.rectangle.angled")
                    .foregroundStyle(.secondary)
            )
    }
}
