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
        let cgImage = makeOpaque(thumbnail)

        var quality = startQuality
        for _ in 0...qualitySteps {
            let data = try encodeJPEG(cgImage, quality: quality)
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
