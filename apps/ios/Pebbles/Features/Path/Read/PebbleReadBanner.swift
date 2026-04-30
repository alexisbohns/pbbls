import SwiftUI
import os

/// Top zone of the pebble read view.
///
/// Sequencing (issue #335):
/// 1. Phase 1 — render the no-photo layout regardless of whether the pebble
///    has a snap. Pebble centered in its 120pt zone. Photo bytes load in the
///    background; the stroke animation runs in parallel.
/// 2. Phase 2 — once both the stroke animation has finished AND the bytes
///    have been decoded into a `UIImage`, flip `revealPhoto` inside a
///    `withAnimation`. The banner inserts above the pebble box at the
///    bucketed aspect ratio (`BannerAspect`), the pebble box settles into
///    its overlap position, and content below shifts down.
///
/// Without a snap, Phase 2 never fires.
struct PebbleReadBanner: View {
    let snapStoragePath: String?
    let renderSvg: String?
    let renderVersion: String?
    let emotionColorHex: String
    let valence: Valence

    @Environment(SupabaseService.self) private var supabase
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var loadedImage: UIImage?
    @State private var animationFinished: Bool = false
    @State private var revealPhoto: Bool = false

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-read-banner")

    private let bannerCornerRadius: CGFloat = 24
    private let boxSize: CGFloat = 120
    private let boxCornerRadius: CGFloat = 24
    private let revealDuration: Double = 0.45
    private let revealDurationReduceMotion: Double = 0.25

    var body: some View {
        VStack(spacing: 0) {
            if revealPhoto, let image = loadedImage {
                bannerWithPhoto(image: image)
                    .transition(reduceMotion ? .opacity : .opacity.combined(with: .move(edge: .top)))
            } else {
                renderedPebble
                    .frame(maxWidth: .infinity, minHeight: boxSize)
            }
        }
        .task(id: snapStoragePath) {
            await loadPhotoIfNeeded()
        }
        .task(id: renderVersion) {
            await waitForAnimationToFinish()
        }
        .onChange(of: loadedImage) { _, _ in revealIfReady() }
        .onChange(of: animationFinished) { _, _ in revealIfReady() }
    }

    private func revealIfReady() {
        guard !revealPhoto, loadedImage != nil, animationFinished else { return }
        let animation: Animation = reduceMotion
            ? .easeOut(duration: revealDurationReduceMotion)
            : .easeOut(duration: revealDuration)
        withAnimation(animation) {
            revealPhoto = true
        }
    }

    // MARK: - Phase 2 banner

    @ViewBuilder
    private func bannerWithPhoto(image: UIImage) -> some View {
        let aspect = BannerAspect.nearest(to: image.size.width / max(image.size.height, 1))
        // Half-overlap pattern: the pebble box renders as an overlay that
        // intentionally extends below the banner's bottom edge. Do NOT wrap
        // this view in a `.clipShape` / `.clipped()` ancestor — the
        // overflow is the design.
        Image(uiImage: image)
            .resizable()
            .aspectRatio(contentMode: .fill)
            .frame(maxWidth: .infinity)
            .aspectRatio(aspect.cgRatio, contentMode: .fit)
            .clipShape(RoundedRectangle(cornerRadius: bannerCornerRadius))
            .accessibilityHidden(true)
            .overlay(alignment: .bottom) {
                pebbleBox
                    .offset(y: boxSize / 2)
            }
            .padding(.bottom, boxSize / 2)
            .frame(maxWidth: .infinity)
    }

    private var pebbleBox: some View {
        renderedPebble
            .frame(width: boxSize, height: boxSize)
            .background(
                RoundedRectangle(cornerRadius: boxCornerRadius)
                    .fill(Color.pebblesBackground)
            )
    }

    // MARK: - Phase 1 background work

    private func loadPhotoIfNeeded() async {
        guard let path = snapStoragePath else { return }
        loadedImage = nil
        revealPhoto = false
        do {
            let urls = try await PebbleSnapRepository(client: supabase.client)
                .signedURLs(storagePrefix: path)
            var request = URLRequest(url: urls.original)
            request.cachePolicy = .reloadIgnoringLocalCacheData
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

    private func waitForAnimationToFinish() async {
        // Static pebble (no animation) → reveal as soon as the photo is ready.
        guard !reduceMotion,
              let timings = PebbleAnimationTimings.forVersion(renderVersion) else {
            animationFinished = true
            return
        }
        do {
            try await Task.sleep(for: .seconds(timings.totalDuration))
        } catch {
            // Cancellation: either the .task id changed (relaunch will reset
            // the gate) or the view disappeared (no reveal needed). Leave
            // animationFinished as-is in both cases.
            return
        }
        animationFinished = true
    }

    // MARK: - Pebble rendering (unchanged)

    @ViewBuilder
    private var renderedPebble: some View {
        if let renderSvg {
            PebbleAnimatedRenderView(
                svg: renderSvg,
                strokeColor: emotionColorHex,
                renderVersion: renderVersion
            )
            .frame(height: pebbleHeight)
        } else {
            EmptyView()
        }
    }

    /// Pebble height inside the 120pt box, scaled by valence size group
    /// so higher-intensity pebbles read bigger than lower-intensity ones
    /// while still fitting comfortably.
    private var pebbleHeight: CGFloat {
        switch valence.sizeGroup {
        case .small:  return 80
        case .medium: return 100
        case .large:  return 116
        }
    }
}

#Preview("Without photo · medium") {
    PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        renderVersion: "0.1.0",
        emotionColorHex: "#7C5CFA",
        valence: .neutralMedium
    )
    .padding()
    .background(Color.pebblesBackground)
}

#Preview("Without photo · large") {
    PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        renderVersion: "0.1.0",
        emotionColorHex: "#7C5CFA",
        valence: .highlightLarge
    )
    .padding()
    .background(Color.pebblesBackground)
}

#Preview("With photo (preview-only stub)") {
    // Preview cannot reach Supabase Storage; this preview only shows the
    // no-photo path. Manual smoke verification (Task 5) covers the with-photo
    // sequencing in the simulator.
    PebbleReadBanner(
        snapStoragePath: nil,
        renderSvg: """
            <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
              <circle cx="50" cy="50" r="40" fill="none" stroke="currentColor" stroke-width="3"/>
            </svg>
            """,
        renderVersion: "0.1.0",
        emotionColorHex: "#7C5CFA",
        valence: .neutralMedium
    )
    .padding()
    .background(Color.pebblesBackground)
}
