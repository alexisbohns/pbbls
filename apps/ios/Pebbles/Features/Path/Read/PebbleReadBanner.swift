import SwiftUI
import os

/// Top zone of the pebble read view (issue #599).
///
/// Loads the snap bytes in the background and composes them with the petroglyph
/// via `SnapPetroglyphHeader`:
/// - With a snap → the photo (cover-cropped to its `BannerAspect` bucket, tilted)
///   with the petroglyph overlapping the top-right corner.
/// - Without a snap (or after a settled load failure) → the petroglyph centered
///   as the page heading.
///
/// The petroglyph and snap appear together — the petroglyph keeps its own
/// entry animation (`PebbleAnimatedRenderView`); there is no timed reveal gate.
struct PebbleReadBanner: View {
    let snapStoragePath: String?
    let renderSvg: String?
    let renderVersion: String?
    let emotionId: UUID
    let valence: Valence

    @Environment(SnapURLCache.self) private var snapURLs
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(\.colorScheme) private var colorScheme

    @State private var loadedImage: UIImage?
    /// True once a snap load attempt has settled in failure (no image). Used to
    /// collapse to the centered layout instead of holding an empty placeholder.
    @State private var loadFailed: Bool = false

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-read-banner")

    /// A snap slot is expected when the pebble has a storage path AND the load
    /// hasn't hard-failed. A still-loading snap keeps the slot (placeholder);
    /// only a settled failure drops to the centered petroglyph.
    private var hasSnapSlot: Bool {
        snapStoragePath != nil && !loadFailed
    }

    var body: some View {
        SnapPetroglyphHeader(snapImage: loadedImage, hasSnapSlot: hasSnapSlot) {
            renderedPebble
        }
        .frame(maxWidth: .infinity)
        .task(id: snapStoragePath) {
            await loadPhotoIfNeeded()
        }
    }

    // MARK: - Petroglyph (backfill + outline + glyph)

    @ViewBuilder
    private var renderedPebble: some View {
        if let renderSvg {
            let palette = palettes.palette(for: emotionId)
            let colors = palette?.petroglyphColors(forIntensity: valence.intensity, scheme: colorScheme)
            let strokeHex = colors?.strokeHex ?? Color.accent.primaryHex
            PebbleAnimatedRenderView(
                svg: renderSvg,
                strokeColor: Color(hex: strokeHex) ?? Color.accent.primary,
                strokeColorHex: strokeHex,
                fillHex: colors?.fillHex ?? Color.accent.primaryHex,
                fillOpacity: colors?.fillOpacity ?? 1,
                size: valence.sizeGroup,
                polarity: valence.polarity,
                renderVersion: renderVersion
            )
        } else {
            EmptyView()
        }
    }

    // MARK: - Snap load

    private func loadPhotoIfNeeded() async {
        loadedImage = nil
        loadFailed = false
        guard let path = snapStoragePath else { return }
        do {
            let urls = try await snapURLs.signedURLs(storagePath: path)
            var request = URLRequest(url: urls.original)
            request.timeoutInterval = 30
            let (data, _) = try await URLSession.shared.data(for: request)
            guard let image = UIImage(data: data) else {
                Self.logger.error("decode failed for \(path, privacy: .public)")
                loadFailed = true
                return
            }
            loadedImage = image
        } catch {
            Self.logger.error(
                "photo load failed for \(path, privacy: .public): \(error.localizedDescription, privacy: .private)"
            )
            loadFailed = true
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
