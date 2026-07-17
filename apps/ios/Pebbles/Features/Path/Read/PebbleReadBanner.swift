import SwiftUI
import os

/// Top zone of the pebble read view — the #599 redesign.
///
/// - **No snap** (or one that hasn't loaded / failed to load): the framed
///   `PebbleReadPetroglyph` centered as the page heading.
/// - **Snap present**: once the ORIGINAL rendition is signed + decoded, the
///   whole picture shows at its nearest `BannerAspect` bucket with the
///   Petroglyph overlapping its top-right (`PebbleSnapFrame`).
///
/// This replaces the previous two-phase reveal (the snap sliding in over the
/// pebble after the stroke animation, #335 / #582): the page now frames the
/// picture outright, so there is no mask/slide. A failed load simply stays on
/// the Petroglyph heading — no error UI. The Petroglyph keeps its own native
/// draw-on (via `PebbleAnimatedRenderView`); nothing gates the snap swap but
/// the decode itself.
struct PebbleReadBanner: View {
    let snapStoragePath: String?
    let renderSvg: String?
    let renderVersion: String?
    let emotionId: UUID
    let valence: Valence

    @Environment(SnapURLCache.self) private var snapURLs
    @Environment(EmotionPaletteService.self) private var palettes

    @State private var loadedImage: UIImage?

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-read-banner")

    /// Square slot for the Petroglyph when it is the page heading (no snap).
    private let headingPetroglyph: CGFloat = 150

    var body: some View {
        Group {
            if let image = loadedImage {
                PebbleSnapFrame(
                    image: image,
                    aspect: BannerAspect.nearest(to: image.size.width / max(image.size.height, 1)),
                    renderSvg: renderSvg,
                    renderVersion: renderVersion,
                    valence: valence,
                    palette: palette
                )
            } else {
                PebbleReadPetroglyph(
                    renderSvg: renderSvg,
                    renderVersion: renderVersion,
                    valence: valence,
                    palette: palette
                )
                .frame(width: headingPetroglyph, height: headingPetroglyph)
                .frame(maxWidth: .infinity)
            }
        }
        .task(id: snapStoragePath) {
            await loadPhotoIfNeeded()
        }
    }

    private var palette: EmotionPalette? {
        palettes.palette(for: emotionId)
    }

    private func loadPhotoIfNeeded() async {
        guard let path = snapStoragePath else { return }
        loadedImage = nil
        do {
            let urls = try await snapURLs.signedURLs(storagePath: path)
            var request = URLRequest(url: urls.original)
            request.timeoutInterval = 30
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let image = UIImage(data: data) else {
                Self.logger.error(
                    "decode failed for \(path, privacy: .public)"
                )
                return
            }
            loadedImage = image
        } catch {
            Self.logger.error(
                "photo load failed for \(path, privacy: .public): \(error.localizedDescription, privacy: .private)"
            )
        }
    }
}

#Preview("Without photo · medium") {
    let supabase = SupabaseService()
    return PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        renderVersion: "0.1.0",
        emotionId: UUID(),
        valence: .neutralMedium
    )
    .padding()
    .background(Color.system.background)
    .environment(supabase)
    .environment(EmotionPaletteService(client: supabase.client))
    .environment(SnapURLCache(client: supabase.client))
}

#Preview("Without photo · large") {
    let supabase = SupabaseService()
    return PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        renderVersion: "0.1.0",
        emotionId: UUID(),
        valence: .highlightLarge
    )
    .padding()
    .background(Color.system.background)
    .environment(supabase)
    .environment(EmotionPaletteService(client: supabase.client))
    .environment(SnapURLCache(client: supabase.client))
}

#Preview("With photo (preview-only stub)") {
    // Preview cannot reach Supabase Storage, so the decoded-snap path can't
    // load here; this preview shows the no-photo heading. The `PebbleSnapFrame`
    // overlay is previewed directly in its own file with a stand-in image.
    let supabase = SupabaseService()
    return PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        renderVersion: "0.1.0",
        emotionId: UUID(),
        valence: .neutralMedium
    )
    .padding()
    .background(Color.system.background)
    .environment(supabase)
    .environment(EmotionPaletteService(client: supabase.client))
    .environment(SnapURLCache(client: supabase.client))
}
