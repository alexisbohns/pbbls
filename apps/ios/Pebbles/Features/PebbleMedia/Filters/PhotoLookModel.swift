import CoreGraphics
import CoreImage
import Observation
import os

/// The Filter and Adjust tools' state (spec 2026-11-23 "Architecture"). It works on the
/// crop at upload size, so the preview is the upload. Look changes coalesce: at most one
/// render runs, and one more follows if the look moved meanwhile.
@Observable @MainActor
final class PhotoLookModel {
    static let thumbnailEdge = 192

    var look = PhotoLook.untouched {
        didSet { if look != oldValue { lookChanged() } }
    }
    var showsOriginal = false
    private(set) var base: CGImage?
    private(set) var preparedFor: CropGeometry?
    private(set) var preview: CGImage?
    private(set) var originalThumbnail: CGImage?
    private(set) var thumbnails: [FilterPreset: CGImage] = [:]
    private(set) var masks = FilterMasks.none

    @ObservationIgnored private var rendering = false
    @ObservationIgnored private var dirty = false
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "photo-look")

    /// Called with each new crop, before Filter or Adjust shows it.
    func prepare(_ cropped: CGImage, for geometry: CropGeometry) async {
        base = cropped
        preparedFor = geometry
        masks = .none
        let small = Self.downscaled(cropped, longEdge: Self.thumbnailEdge)
        originalThumbnail = small
        thumbnails = await Task.detached(priority: .userInitiated) {
            var out: [FilterPreset: CGImage] = [:]
            guard let small else { return out }
            for preset in FilterPreset.allCases {
                if let image = Self.render(preset.params, on: small, masks: .none) { out[preset] = image }
            }
            return out
        }.value
        if thumbnails.count != FilterPreset.allCases.count { logger.error("preset thumbnails incomplete") }
        // The first preview is awaited, so a freshly prepared model already shows the look.
        let params = look.resolve(), masks = self.masks
        preview = await Task.detached(priority: .userInitiated) { Self.render(params, on: cropped, masks: masks) }.value
        if preview == nil { logger.error("look preview render failed") }
    }

    /// Done: the look on the 1024 px crop, or the crop itself when the look is the identity.
    /// `sameCrop` says whether `cropped` matches the crop this model was prepared with.
    func apply(to cropped: CGImage, sameCrop: Bool) async -> CGImage {
        let params = look.resolve()
        guard params != .neutral else { return cropped }
        let masks = sameCrop ? self.masks : .none
        let rendered = await Task.detached(priority: .userInitiated) {
            Self.render(params, on: cropped, masks: masks)
        }.value
        guard let rendered else {
            logger.error("look render failed on Done; uploading the plain crop")
            return cropped
        }
        return rendered
    }

    private func lookChanged() {
        scheduleRender()
    }

    private func scheduleRender() {
        guard base != nil else { return }
        guard !rendering else {
            dirty = true
            return
        }
        rendering = true
        Task { await renderLoop() }
    }

    private func renderLoop() async {
        repeat {
            dirty = false
            guard let base else { break }
            let params = look.resolve(), masks = self.masks
            let image = await Task.detached(priority: .userInitiated) {
                Self.render(params, on: base, masks: masks)
            }.value
            if let image { preview = image } else { logger.error("look preview render failed") }
        } while dirty
        rendering = false
    }

    /// Renders in the image's own colour space, as the lab does: a Display P3 photo stays P3.
    nonisolated static func render(_ params: FilterParams, on image: CGImage, masks: FilterMasks) -> CGImage? {
        guard let space = image.colorSpace ?? CGColorSpace(name: CGColorSpace.sRGB) else { return nil }
        let output = FilterPipeline.apply(params, to: CIImage(cgImage: image), masks: masks)
        return FilterPipeline.render(output, context: FilterPipeline.sharedContext, colorSpace: space)
    }

    nonisolated static func downscaled(_ image: CGImage, longEdge: Int) -> CGImage? {
        let scale = Double(longEdge) / Double(max(image.width, image.height))
        let width = max(1, Int((Double(image.width) * scale).rounded()))
        let height = max(1, Int((Double(image.height) * scale).rounded()))
        guard let space = image.colorSpace ?? CGColorSpace(name: CGColorSpace.sRGB),
              let ctx = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                                  space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return nil }
        ctx.interpolationQuality = .high
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return ctx.makeImage()
    }
}
