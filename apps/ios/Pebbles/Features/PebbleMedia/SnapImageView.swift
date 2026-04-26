import SwiftUI

/// Lazy-loads signed URLs for a single snap and renders the original (1024 px)
/// JPEG. Designed for the pebble detail sheet — caller passes the
/// `storage_path` from `public.snaps`, no auth/user knowledge required.
struct SnapImageView: View {
    let storagePath: String

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
                            .frame(maxWidth: .infinity)
                            .frame(height: 200)
                    case .failure:
                        fallbackPlaceholder
                    case .success(let image):
                        image
                            .resizable()
                            .scaledToFit()
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    @unknown default:
                        fallbackPlaceholder
                    }
                }
            } else if loadError {
                fallbackPlaceholder
            } else {
                ProgressView()
                    .frame(maxWidth: .infinity)
                    .frame(height: 200)
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
        RoundedRectangle(cornerRadius: 12)
            .fill(Color.secondary.opacity(0.1))
            .frame(maxWidth: .infinity)
            .frame(height: 200)
            .overlay(
                Image(systemName: "photo.on.rectangle.angled")
                    .foregroundStyle(.secondary)
            )
    }
}
