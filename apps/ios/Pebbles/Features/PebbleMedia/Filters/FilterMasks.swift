import CoreImage
import Vision
import os

/// What one photo needs beyond its values (spec "The controls"), computed once per photo.
/// `@unchecked Sendable`: it crosses into detached renders, and `FilterPipeline`
/// copies each `CIFilter` before setting its input, so the shared instances are never mutated.
struct FilterMasks: @unchecked Sendable {
    /// White on the subject, black elsewhere, at the photo's extent. Nil when
    /// Vision found none, which disables Refocus for that photo.
    var subject: CIImage?
    /// Apple's suggested auto-enhance and red-eye chain for this photo, in order.
    var autoEnhance: [CIFilter]

    static let none = FilterMasks(subject: nil, autoEnhance: [])

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "filter-masks")

    static func compute(for image: CGImage, faceCount: Int) -> FilterMasks {
        FilterMasks(subject: subjectMask(for: image, faceCount: faceCount),
                    autoEnhance: autoEnhanceFilters(for: image))
    }

    static func autoEnhanceFilters(for image: CGImage) -> [CIFilter] {
        CIImage(cgImage: image).autoAdjustmentFilters(options: [.enhance: true, .redEye: true])
    }

    /// Faces present → the person mask; otherwise (or when the person request
    /// finds nobody, since a face count can be a poster) the general foreground mask.
    /// The mask is read raw, not colour-managed: its grey values are coverage.
    static func subjectMask(for image: CGImage, faceCount: Int) -> CIImage? {
        let handler = VNImageRequestHandler(cgImage: image)
        let extent = CGRect(x: 0, y: 0, width: image.width, height: image.height)
        do {
            if faceCount > 0 {
                let request = VNGeneratePersonSegmentationRequest()
                request.qualityLevel = .accurate
                request.outputPixelFormat = kCVPixelFormatType_OneComponent8
                try handler.perform([request])
                if let buffer = request.results?.first?.pixelBuffer {
                    return fitted(CIImage(cvPixelBuffer: buffer, options: [.colorSpace: NSNull()]), to: extent)
                }
            }
            let request = VNGenerateForegroundInstanceMaskRequest()
            try handler.perform([request])
            guard let result = request.results?.first, !result.allInstances.isEmpty else {
                logger.debug("no subject found")
                return nil
            }
            let buffer = try result.generateScaledMaskForImage(forInstances: result.allInstances, from: handler)
            return fitted(CIImage(cvPixelBuffer: buffer, options: [.colorSpace: NSNull()]), to: extent)
        } catch {
            logger.error("subject mask failed: \(error.localizedDescription, privacy: .public)")
            return nil
        }
    }

    private static func fitted(_ mask: CIImage, to extent: CGRect) -> CIImage {
        let sx = extent.width / mask.extent.width
        let sy = extent.height / mask.extent.height
        return mask.transformed(by: CGAffineTransform(scaleX: sx, y: sy)).cropped(to: extent)
    }
}
