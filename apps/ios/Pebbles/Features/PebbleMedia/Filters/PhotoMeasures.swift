import CoreImage
import ImageIO
import Vision
import os

/// Facts about one photo for the preset analysis (spec "Architecture"),
/// computed once at import and exported with the looks.
struct PhotoMeasures: Codable, Equatable, Sendable {
    struct Scene: Codable, Equatable, Sendable {
        var identifier: String
        var confidence: Double
    }

    /// One filter of Apple's auto-enhance chain: its name and numeric inputs
    /// (a number as one value, a vector as its components).
    struct AutoEnhanceStep: Codable, Equatable, Sendable {
        var filter: String
        var values: [String: [Double]]
    }

    /// Luma at the 5th, 25th, 50th, 75th and 95th percentiles, 0…1, sRGB-encoded.
    var luminance: [Double]
    var meanSaturation: Double
    /// Mean red − green, and mean (red + green) / 2 − blue, −1…1.
    var castRedGreen: Double
    var castYellowBlue: Double
    var faceCount: Int
    /// Vision's top five scene tags.
    var scenes: [Scene]
    var hasDepth: Bool
    /// Vision's overall aesthetics score, −1…1; nil before iOS 18.
    var aesthetics: Double?
    var autoEnhance: [AutoEnhanceStep]
}

/// The tone half of the measures, pure so it can be tested on synthetic pictures.
struct ToneStats: Equatable {
    var luminance: [Double]
    var meanSaturation: Double
    var castRedGreen: Double
    var castYellowBlue: Double

    static let empty = ToneStats(luminance: [0, 0, 0, 0, 0], meanSaturation: 0, castRedGreen: 0, castYellowBlue: 0)
}

extension ToneStats {
    /// Samples the photo at no more than 256 px on its long edge, in sRGB.
    init?(_ image: CGImage) {
        let scale = min(1, 256 / CGFloat(max(image.width, image.height)))
        let w = max(1, Int((CGFloat(image.width) * scale).rounded()))
        let h = max(1, Int((CGFloat(image.height) * scale).rounded()))
        guard let space = CGColorSpace(name: CGColorSpace.sRGB),
              let ctx = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8, bytesPerRow: w * 4,
                                  space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue) else { return nil }
        ctx.interpolationQuality = .medium
        ctx.draw(image, in: CGRect(x: 0, y: 0, width: w, height: h))
        guard let base = ctx.data else { return nil }
        let px = base.bindMemory(to: UInt8.self, capacity: w * h * 4)
        var lumas: [Double] = []
        lumas.reserveCapacity(w * h)
        var saturation = 0.0, redGreen = 0.0, yellowBlue = 0.0
        for i in 0..<(w * h) {
            let r = Double(px[i * 4]) / 255, g = Double(px[i * 4 + 1]) / 255, b = Double(px[i * 4 + 2]) / 255
            lumas.append(0.2126 * r + 0.7152 * g + 0.0722 * b)
            let high = max(r, g, b), low = min(r, g, b)
            saturation += high > 0 ? (high - low) / high : 0
            redGreen += r - g
            yellowBlue += (r + g) / 2 - b
        }
        lumas.sort()
        let n = Double(lumas.count)
        self.init(luminance: [0.05, 0.25, 0.5, 0.75, 0.95].map { lumas[min(lumas.count - 1, Int($0 * n))] },
                  meanSaturation: saturation / n, castRedGreen: redGreen / n, castYellowBlue: yellowBlue / n)
    }
}

extension PhotoMeasures {
    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "photo-measures")

    /// Everything but the face count and depth flag, which the caller already has.
    /// Runs Vision: call it off the main actor.
    static func measure(_ image: CGImage, faceCount: Int, hasDepth: Bool, autoEnhance: [CIFilter]) -> PhotoMeasures {
        let tone: ToneStats
        if let stats = ToneStats(image) {
            tone = stats
        } else {
            logger.error("tone stats unavailable for a \(image.width)×\(image.height) photo")
            tone = .empty
        }
        return PhotoMeasures(luminance: tone.luminance, meanSaturation: tone.meanSaturation,
                             castRedGreen: tone.castRedGreen, castYellowBlue: tone.castYellowBlue,
                             faceCount: faceCount, scenes: scenes(in: image), hasDepth: hasDepth,
                             aesthetics: aesthetics(of: image), autoEnhance: autoEnhance.map(step))
    }

    static func faceCount(in image: CGImage) -> Int {
        let request = VNDetectFaceRectanglesRequest()
        do {
            try VNImageRequestHandler(cgImage: image).perform([request])
        } catch {
            logger.error("face detection failed: \(error.localizedDescription, privacy: .public)")
            return 0
        }
        return request.results?.count ?? 0
    }

    static func scenes(in image: CGImage) -> [Scene] {
        let request = VNClassifyImageRequest()
        do {
            try VNImageRequestHandler(cgImage: image).perform([request])
        } catch {
            logger.error("scene classification failed: \(error.localizedDescription, privacy: .public)")
            return []
        }
        return (request.results ?? [])
            .sorted { $0.confidence > $1.confidence }
            .prefix(5)
            .map { Scene(identifier: $0.identifier, confidence: Double($0.confidence)) }
    }

    static func aesthetics(of image: CGImage) -> Double? {
        guard #available(iOS 18.0, macOS 15.0, *) else { return nil }
        let request = VNCalculateImageAestheticsScoresRequest()
        do {
            try VNImageRequestHandler(cgImage: image).perform([request])
        } catch {
            logger.error("aesthetics score failed: \(error.localizedDescription, privacy: .public)")
            return nil
        }
        return request.results?.first.map { Double($0.overallScore) }
    }

    /// Read from the original bytes: the crop editor's working image carries no depth.
    static func hasDepth(_ data: Data) -> Bool {
        guard let source = CGImageSourceCreateWithData(data as CFData, nil) else { return false }
        return [kCGImageAuxiliaryDataTypeDisparity, kCGImageAuxiliaryDataTypeDepth].contains { type in
            CGImageSourceCopyAuxiliaryDataInfoAtIndex(source, 0, type) != nil
        }
    }

    static func step(_ filter: CIFilter) -> AutoEnhanceStep {
        var values: [String: [Double]] = [:]
        for key in filter.inputKeys where key != kCIInputImageKey {
            switch filter.value(forKey: key) {
            case let number as NSNumber: values[key] = [number.doubleValue]
            case let vector as CIVector: values[key] = (0..<vector.count).map { Double(vector.value(at: $0)) }
            default: break
            }
        }
        return AutoEnhanceStep(filter: filter.name, values: values)
    }
}
