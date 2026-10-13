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
/// the Petroglyph heading — no error UI. The Petroglyph is the lit stone (the
/// derived glyph, or the blank stone for a bare moment) with its light sweep;
/// nothing gates the snap swap but the decode itself.
struct PebbleReadBanner: View {
    let snapStoragePath: String?
    /// Nil for a bare moment: the blank stone, and the banner falls back to
    /// the system chrome.
    let pebble: MomentDetail.Pebble?

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
                    pebble: pebble,
                    palette: palette
                )
            } else {
                PebbleReadPetroglyph(pebble: pebble, palette: palette)
                .frame(width: headingPetroglyph, height: headingPetroglyph)
                .frame(maxWidth: .infinity)
            }
        }
        .task(id: snapStoragePath) {
            await loadPhotoIfNeeded()
        }
    }

    private var palette: EmotionPalette? {
        pebble.flatMap { palettes.palette(for: $0.emotion.id) }
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
        pebble: nil
    )
    .padding()
    .background(Color.system.background)
    .environment(supabase)
    .environment(EmotionPaletteService(client: supabase.client))
    .environment(SnapURLCache(client: supabase.client))
}

#Preview("Without photo · bare moment") {
    let supabase = SupabaseService()
    return PebbleReadBanner(
        snapStoragePath: nil,
        pebble: nil
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
        pebble: nil
    )
    .padding()
    .background(Color.system.background)
    .environment(supabase)
    .environment(EmotionPaletteService(client: supabase.client))
    .environment(SnapURLCache(client: supabase.client))
}
