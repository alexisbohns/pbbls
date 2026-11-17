import CoreImage
import CoreImage.CIFilterBuiltins

/// The filter lab's pipeline (spec 2026-11-17 "The controls"), kept for the
/// future Filters tab. Pure: no UIKit, no logging, so it also compiles on macOS
/// for the analysis. Each step returns its input untouched at zero, so neutral
/// values are an exact identity. The order is fixed.
enum FilterPipeline {
    /// Pixel radii are authored for a 1024 px long edge and scale with the photo.
    static let referenceEdge: CGFloat = 1024

    /// One context for the app's lifetime: creating one per render is the expensive part.
    /// The default context works in linear extended sRGB, so the curve points, the
    /// contrast pivot and the soft-light neutral act on linear values. The macOS
    /// analysis wrapper must use a default `CIContext()` too, or it measures another look.
    static let sharedContext = CIContext()

    static func apply(_ p: FilterParams, to input: CIImage, masks: FilterMasks = .none) -> CIImage {
        let extent = input.extent
        let unit = max(extent.width, extent.height) / referenceEdge
        var image = input
        if p.autoEnhance { image = autoEnhanced(image, masks.autoEnhance) }
        image = exposure(image, p.exposure)
        image = brilliance(image, p.brilliance)
        image = highlights(image, p.highlights)
        image = shadows(image, p.shadows)
        image = contrastSaturation(image, contrast: p.contrast, saturation: p.saturation)
        image = vibrance(image, p.vibrance)
        image = toneTint(image, tone: p.tone, tint: p.tint)
        image = sharpness(image, p.sharpness, unit: unit)
        image = refocus(image, p.refocus, mask: masks.subject, unit: unit)
        image = vignette(image, p.vignette, extent: extent)
        image = blurVignette(image, p.blurVignette, extent: extent, unit: unit)
        image = grain(image, p.grain, extent: extent, unit: unit)
        return image.cropped(to: extent)
    }

    /// Renders in `colorSpace`: pass the photo's own, so a Display P3 photo stays P3.
    static func render(_ image: CIImage, context: CIContext, colorSpace: CGColorSpace) -> CGImage? {
        context.createCGImage(image, from: image.extent, format: .RGBA8, colorSpace: colorSpace)
    }

    static func autoEnhanced(_ image: CIImage, _ filters: [CIFilter]) -> CIImage {
        filters.reduce(image) { current, filter in
            // Copied: the same chain serves the preview and the export on different threads.
            guard let step = filter.copy() as? CIFilter else { return current }
            step.setValue(current, forKey: kCIInputImageKey)
            return step.outputImage ?? current
        }
    }

    static func exposure(_ image: CIImage, _ v: Double) -> CIImage {
        guard v != 0 else { return image }
        let f = CIFilter.exposureAdjust()
        f.inputImage = image
        f.ev = Float(2 * v)
        return f.outputImage ?? image
    }

    /// Photos' Brilliance is unpublished: this lifts the shadows, pulls the
    /// highlights and steepens the midtones.
    static func brilliance(_ image: CIImage, _ v: Double) -> CIImage {
        guard v != 0 else { return image }
        let f = CIFilter.highlightShadowAdjust()
        f.inputImage = image
        f.shadowAmount = Float(0.6 * v)
        f.highlightAmount = Float(1 - 0.4 * max(v, 0))
        return curve(f.outputImage ?? image, quarter: 0.25 - 0.03 * v, half: 0.5, threeQuarter: 0.75 + 0.03 * v)
    }

    static func highlights(_ image: CIImage, _ v: Double) -> CIImage {
        guard v != 0 else { return image }
        guard v > 0 else {
            let f = CIFilter.highlightShadowAdjust()
            f.inputImage = image
            f.highlightAmount = Float(1 + v)
            return f.outputImage ?? image
        }
        return curve(image, quarter: 0.25, half: 0.5 + 0.02 * v, threeQuarter: 0.75 + 0.1 * v)
    }

    static func shadows(_ image: CIImage, _ v: Double) -> CIImage {
        guard v != 0 else { return image }
        let f = CIFilter.highlightShadowAdjust()
        f.inputImage = image
        f.shadowAmount = Float(v)
        return f.outputImage ?? image
    }

    static func contrastSaturation(_ image: CIImage, contrast: Double, saturation: Double) -> CIImage {
        guard contrast != 0 || saturation != 0 else { return image }
        let f = CIFilter.colorControls()
        f.inputImage = image
        f.contrast = Float(1 + 0.5 * contrast)
        f.saturation = Float(1 + saturation)
        f.brightness = 0
        return f.outputImage ?? image
    }

    static func vibrance(_ image: CIImage, _ v: Double) -> CIImage {
        guard v != 0 else { return image }
        let f = CIFilter.vibrance()
        f.inputImage = image
        f.amount = Float(v)
        return f.outputImage ?? image
    }

    /// Tone > 0 is warmer, tint > 0 is magenta. Checked on Core Image 2026-11-17:
    /// a lower target temperature warms the picture, a positive target tint greens it.
    static func toneTint(_ image: CIImage, tone: Double, tint: Double) -> CIImage {
        guard tone != 0 || tint != 0 else { return image }
        let f = CIFilter.temperatureAndTint()
        f.inputImage = image
        f.neutral = CIVector(x: 6500, y: 0)
        f.targetNeutral = CIVector(x: 6500 - 2000 * tone, y: -30 * tint)
        return f.outputImage ?? image
    }

    static func sharpness(_ image: CIImage, _ v: Double, unit: CGFloat) -> CIImage {
        guard v != 0 else { return image }
        let f = CIFilter.sharpenLuminance()
        f.inputImage = image.clampedToExtent()
        f.sharpness = Float(0.8 * v)
        f.radius = Float(1.5 * unit)
        return (f.outputImage ?? image).cropped(to: image.extent)
    }

    /// Blurs everything but the subject. No mask, no refocus.
    static func refocus(_ image: CIImage, _ v: Double, mask: CIImage?, unit: CGFloat) -> CIImage {
        guard v != 0, let mask else { return image }
        let fitted = fit(mask, to: image.extent)
        return maskedBlur(image, mask: fitted.applyingFilter("CIColorInvert"), radius: 20 * v * unit)
    }

    static func vignette(_ image: CIImage, _ v: Double, extent: CGRect) -> CIImage {
        guard v != 0 else { return image }
        let f = CIFilter.vignetteEffect()
        f.inputImage = image
        f.center = CGPoint(x: extent.midX, y: extent.midY)
        f.radius = Float(hypot(extent.width, extent.height) / 2 * 0.7)
        f.intensity = Float(v)
        f.falloff = 0.5
        return f.outputImage ?? image
    }

    static func blurVignette(_ image: CIImage, _ v: Double, extent: CGRect, unit: CGFloat) -> CIImage {
        guard v != 0 else { return image }
        let g = CIFilter.radialGradient()
        g.center = CGPoint(x: extent.midX, y: extent.midY)
        g.radius0 = Float(min(extent.width, extent.height) * 0.35)
        g.radius1 = Float(hypot(extent.width, extent.height) / 2)
        g.color0 = CIColor.black
        g.color1 = CIColor.white
        guard let mask = g.outputImage?.cropped(to: extent) else { return image }
        return maskedBlur(image, mask: mask, radius: 12 * v * unit)
    }

    /// Luminance-only noise, soft-light blended: 0.5 grey is neutral in soft light,
    /// so the strength scales the noise around it. The generator is deterministic.
    static func grain(_ image: CIImage, _ v: Double, extent: CGRect, unit: CGFloat) -> CIImage {
        guard v != 0, let noise = CIFilter.randomGenerator().outputImage else { return image }
        // The generator's alpha is noise too; fixing it at 1 keeps the matrix output opaque.
        let random = noise.settingAlphaOne(in: .infinite)
        let k = CGFloat(0.25 * v), bias = 0.5 - 0.5 * k
        let grey = random.applyingFilter("CIColorMatrix", parameters: [
            "inputRVector": CIVector(x: k, y: 0, z: 0, w: 0),
            "inputGVector": CIVector(x: k, y: 0, z: 0, w: 0),
            "inputBVector": CIVector(x: k, y: 0, z: 0, w: 0),
            "inputAVector": CIVector(x: 0, y: 0, z: 0, w: 0),
            "inputBiasVector": CIVector(x: bias, y: bias, z: bias, w: 1)
        ])
        .transformed(by: CGAffineTransform(scaleX: unit, y: unit))
        .cropped(to: extent)
        let f = CIFilter.softLightBlendMode()
        f.inputImage = grey
        f.backgroundImage = image
        return f.outputImage ?? image
    }

    /// Scales a mask to the picture's extent, so a preview rendered at another
    /// size still lines up with the mask computed at the photo's own.
    private static func fit(_ mask: CIImage, to extent: CGRect) -> CIImage {
        let source = mask.extent
        guard source != extent, source.width > 0, source.height > 0 else { return mask }
        let transform = CGAffineTransform(translationX: -source.minX, y: -source.minY)
            .concatenating(CGAffineTransform(scaleX: extent.width / source.width, y: extent.height / source.height))
            .concatenating(CGAffineTransform(translationX: extent.minX, y: extent.minY))
        return mask.transformed(by: transform).cropped(to: extent)
    }

    private static func curve(_ image: CIImage, quarter: Double, half: Double, threeQuarter: Double) -> CIImage {
        let f = CIFilter.toneCurve()
        f.inputImage = image
        f.point0 = .zero
        f.point1 = CGPoint(x: 0.25, y: quarter)
        f.point2 = CGPoint(x: 0.5, y: half)
        f.point3 = CGPoint(x: 0.75, y: threeQuarter)
        f.point4 = CGPoint(x: 1, y: 1)
        return f.outputImage ?? image
    }

    private static func maskedBlur(_ image: CIImage, mask: CIImage, radius: CGFloat) -> CIImage {
        let f = CIFilter.maskedVariableBlur()
        f.inputImage = image.clampedToExtent()
        f.mask = mask.clampedToExtent()
        f.radius = Float(radius)
        return (f.outputImage ?? image).cropped(to: image.extent)
    }
}
