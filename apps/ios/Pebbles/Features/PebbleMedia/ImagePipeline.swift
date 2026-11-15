import Foundation
import ImageIO
import CoreGraphics
import UniformTypeIdentifiers

/// Output of `ImagePipeline.process`. Both blobs are JPEG bytes ready to upload
/// to Supabase Storage; no further processing required.
struct ProcessedImage: Equatable {
    /// JPEG, max 1024 px on the long edge, target ≤1 MB.
    let original: Data
    /// JPEG, max 420 px on the long edge, target ≤300 KB.
    let thumb: Data
}

enum ImagePipelineError: Error, Equatable {
    case unsupportedFormat
    case decodeFailed
    case encodeFailed
    case tooLargeAfterResize
}

enum ImagePipeline {

    private static let originalMaxEdge: CGFloat = 1024
    private static let thumbMaxEdge:    CGFloat = 420

    private static let originalMaxBytes = 1_048_576   // 1 MB
    private static let thumbMaxBytes    =   307_200   // 300 KB
    private static let qualitySteps = 3

    private static let originalStartQuality: CGFloat = 0.85
    private static let thumbStartQuality:    CGFloat = 0.75

    /// Validate, decode, resize, re-encode as two JPEGs without metadata.
    /// Pure: no I/O, no logging, no global state.
    static func process(_ source: Data, uti: String) throws -> ProcessedImage {
        guard ImageFormatValidator.isSupported(uti) else {
            throw ImagePipelineError.unsupportedFormat
        }

        let imageSource = try makeImageSource(source)

        let original = try renderJPEG(
            from: imageSource,
            maxEdge: originalMaxEdge,
            startQuality: originalStartQuality,
            byteCap: originalMaxBytes
        )
        let thumb = try renderJPEG(
            from: imageSource,
            maxEdge: thumbMaxEdge,
            startQuality: thumbStartQuality,
            byteCap: thumbMaxBytes
        )

        return ProcessedImage(original: original, thumb: thumb)
    }

    /// The crop editor's output (spec "Pipeline"): already upright, already
    /// cropped, carrying no metadata. Same sizes, caps and encode as a pick.
    static func process(_ image: CGImage) throws -> ProcessedImage {
        let original = try encodeCapped(
            resized(image, maxEdge: originalMaxEdge), startQuality: originalStartQuality, byteCap: originalMaxBytes)
        let thumb = try encodeCapped(
            resized(image, maxEdge: thumbMaxEdge), startQuality: thumbStartQuality, byteCap: thumbMaxBytes)
        return ProcessedImage(original: original, thumb: thumb)
    }

    /// Decodes `data` upright (EXIF orientation baked) with its long edge at
    /// most `maxEdge`: the crop editor's working image.
    static func decodeOriented(_ data: Data, maxEdge: CGFloat) throws -> CGImage {
        let source = try makeImageSource(data)
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceThumbnailMaxPixelSize: maxEdge
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            throw ImagePipelineError.decodeFailed
        }
        return image
    }

    /// The colour space an 8-bit opaque RGB bitmap of `image` is drawn in: its
    /// own when that is RGB (a Display P3 photo keeps its gamut), else sRGB.
    /// The same choice `makeOpaque` makes.
    static func drawingColorSpace(for image: CGImage) -> CGColorSpace? {
        if let source = image.colorSpace, source.model == .rgb {
            return source
        }
        return CGColorSpace(name: CGColorSpace.sRGB)
    }

    /// Downscales into an opaque bitmap (in the image's own RGB space) with its
    /// long edge at most `maxEdge`; never upscales. Opaque either way, as `makeOpaque`.
    static func resized(_ image: CGImage, maxEdge: CGFloat) -> CGImage {
        let longEdge = CGFloat(max(image.width, image.height))
        guard longEdge > maxEdge else { return makeOpaque(image) }
        let factor = maxEdge / longEdge
        let width = Int((CGFloat(image.width) * factor).rounded())
        let height = Int((CGFloat(image.height) * factor).rounded())
        guard let space = drawingColorSpace(for: image),
              let context = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8,
                                      bytesPerRow: 0, space: space,
                                      bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else {
            return makeOpaque(image)
        }
        context.interpolationQuality = .high
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return context.makeImage() ?? makeOpaque(image)
    }

    private static func makeImageSource(_ data: Data) throws -> CGImageSource {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil),
              CGImageSourceGetCount(source) > 0 else {
            throw ImagePipelineError.decodeFailed
        }
        return source
    }

    private static func renderJPEG(
        from source: CGImageSource,
        maxEdge: CGFloat,
        startQuality: CGFloat,
        byteCap: Int
    ) throws -> Data {
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceCreateThumbnailWithTransform:   true,    // bake EXIF orientation
            kCGImageSourceShouldCacheImmediately:         true,
            kCGImageSourceThumbnailMaxPixelSize:          maxEdge
        ]
        guard let thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else {
            throw ImagePipelineError.decodeFailed
        }
        // JPEG cannot store alpha. Handing ImageIO an RGBA image makes it log
        // a warning per encode and strip the channel itself, so drop it once
        // here, before the quality-step loop re-encodes the same pixels.
        return try encodeCapped(makeOpaque(thumbnail), startQuality: startQuality, byteCap: byteCap)
    }

    /// Re-encodes at falling quality until the bytes fit `byteCap`.
    private static func encodeCapped(_ image: CGImage, startQuality: CGFloat, byteCap: Int) throws -> Data {
        var quality = startQuality
        for _ in 0...qualitySteps {
            let data = try encodeJPEG(image, quality: quality)
            if data.count <= byteCap {
                return data
            }
            quality -= 0.1
            if quality <= 0.1 { break }
        }
        throw ImagePipelineError.tooLargeAfterResize
    }

    /// Redraws `image` into an opaque 8-bit RGB bitmap (`.noneSkipLast`), or
    /// returns it unchanged when it already carries no alpha.
    ///
    /// `CGImageSourceCreateThumbnailAtIndex` hands back `.premultipliedLast`
    /// even for an opaque photo. Non-RGB sources (grayscale, indexed) are
    /// drawn into sRGB, since an 8-bit `.noneSkipLast` context needs an RGB
    /// colour space. Falls back to the input if the context cannot be built,
    /// which leaves the encode exactly as it was before this step existed.
    static func makeOpaque(_ image: CGImage) -> CGImage {
        switch image.alphaInfo {
        case .none, .noneSkipLast, .noneSkipFirst:
            return image
        default:
            break
        }

        let colorSpace: CGColorSpace
        if let source = image.colorSpace, source.model == .rgb {
            colorSpace = source
        } else if let srgb = CGColorSpace(name: CGColorSpace.sRGB) {
            colorSpace = srgb
        } else {
            return image
        }

        guard let context = CGContext(
            data: nil,
            width: image.width,
            height: image.height,
            bitsPerComponent: 8,
            bytesPerRow: 0,
            space: colorSpace,
            bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue
        ) else {
            return image
        }
        context.draw(image, in: CGRect(x: 0, y: 0, width: image.width, height: image.height))
        return context.makeImage() ?? image
    }

    private static func encodeJPEG(_ image: CGImage, quality: CGFloat) throws -> Data {
        let buffer = NSMutableData()
        // Passing only the lossy-compression key ensures NO EXIF / GPS / TIFF
        // dictionaries are written into the output.
        let options: [CFString: Any] = [
            kCGImageDestinationLossyCompressionQuality: quality
        ]
        guard let destination = CGImageDestinationCreateWithData(
            buffer as CFMutableData,
            UTType.jpeg.identifier as CFString,
            1,
            nil
        ) else {
            throw ImagePipelineError.encodeFailed
        }
        CGImageDestinationAddImage(destination, image, options as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            throw ImagePipelineError.encodeFailed
        }
        return buffer as Data
    }
}
