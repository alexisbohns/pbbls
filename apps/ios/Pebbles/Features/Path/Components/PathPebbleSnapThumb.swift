import SwiftUI
import os

/// Lazy-loads a signed thumb URL for one snap and renders it.
///
/// Sized and clipped by the caller (`PathPebbleRow` wraps it in a 64×64
/// frame, applies a corner radius, white border, drop shadow, and rotation).
/// Failure to sign leaves `url` `nil`; the AsyncImage placeholder shows.
struct PathPebbleSnapThumb: View {
    let storagePath: String

    @Environment(SupabaseService.self) private var supabase
    @State private var url: URL?

    private let logger = Logger(subsystem: "app.pbbls.ios", category: "path-row-thumb")

    var body: some View {
        AsyncImage(url: url) { image in
            image
                .resizable()
                .aspectRatio(contentMode: .fill)
        } placeholder: {
            Color.clear
        }
        .task(id: storagePath) {
            do {
                let urls = try await PebbleSnapRepository(client: supabase.client)
                    .signedURLs(storagePrefix: storagePath)
                url = urls.thumb
            } catch {
                logger.error("snap sign failed: \(error.localizedDescription, privacy: .private)")
            }
        }
    }
}
