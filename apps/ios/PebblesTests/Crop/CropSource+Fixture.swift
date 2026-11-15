import CoreGraphics
@testable import Pebbles

extension CropSource {
    /// A 2:3 gradient (opens on 3:4, so the scrim shows) with a marked corner, for the editor's references.
    static var fixture: CropSource {
        let width = 768, height = 1152
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        let ctx = CGContext(data: nil, width: width, height: height, bitsPerComponent: 8, bytesPerRow: 0,
                            space: space, bitmapInfo: CGImageAlphaInfo.noneSkipLast.rawValue)!
        let colours = [CGColor(red: 0.85, green: 0.6, blue: 0.4, alpha: 1),
                       CGColor(red: 0.3, green: 0.45, blue: 0.6, alpha: 1)] as CFArray
        let gradient = CGGradient(colorsSpace: space, colors: colours, locations: [0, 1])!
        ctx.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: width, y: height), options: [])
        ctx.setFillColor(CGColor(red: 1, green: 1, blue: 1, alpha: 1))
        ctx.fill(CGRect(x: 40, y: height - 200, width: 160, height: 160))
        return CropSource(image: ctx.makeImage()!, captureDate: nil)
    }
}
