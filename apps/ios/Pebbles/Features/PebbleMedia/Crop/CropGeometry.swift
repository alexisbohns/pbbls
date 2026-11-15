import CoreGraphics
import Foundation

/// Where the crop window sits on the photo (spec "Geometry"). Pure value: no
/// view sizes, no SwiftUI. Window units: the window is `ratio.value` wide and
/// 1 tall, centred on the origin, y down; positive turns are clockwise.
struct CropGeometry: Equatable {
    /// The working image's pixel size, upright (EXIF orientation applied).
    var imageSize: CGSize
    var ratio: CropRatio
    /// Clockwise quarter turns, 0…3 once normalised.
    var quarterTurns: Int
    /// Fine turn in degrees, −45…45 once normalised.
    var angle: Double
    /// Window units per image pixel.
    var scale: CGFloat
    /// The image point under the window centre, in pixels from the image centre, y down.
    var focus: CGPoint

    static let maxAngle: Double = 45
    /// How far past the smallest covering scale a pinch may go.
    static let maxZoom: CGFloat = 8
    /// The share of an overshoot the rubber band shows while dragging.
    static let rubberBandFactor: CGFloat = 0.3

    /// The editor's opening state (spec D3): the closest format, centred, covering.
    static func initial(imageSize: CGSize) -> CropGeometry {
        let ratio = CropRatio.closest(to: imageSize.width / max(imageSize.height, 1))
        var g = CropGeometry(imageSize: imageSize, ratio: ratio, quarterTurns: 0, angle: 0, scale: 0, focus: .zero)
        g.scale = g.minScale
        return g
    }

    // MARK: - Derived

    var windowSize: CGSize { CGSize(width: ratio.value, height: 1) }

    /// Total clockwise turn, radians.
    var theta: CGFloat { CGFloat((Double(quarterTurns) * 90 + angle) * .pi / 180) }

    /// The smallest scale at which the turned image covers the window.
    var minScale: CGFloat {
        let c = abs(cos(theta)), s = abs(sin(theta))
        let w = windowSize.width, h = windowSize.height
        let byWidth = (w * c + h * s) / imageSize.width
        let byHeight = (w * s + h * c) / imageSize.height
        return max(byWidth, byHeight)
    }

    var maxScale: CGFloat { minScale * Self.maxZoom }

    /// Half the window's extent along the image's own axes, in image pixels.
    private func halfExtentsInImage(scale: CGFloat) -> CGSize {
        let c = abs(cos(theta)), s = abs(sin(theta))
        let a = windowSize.width / 2, b = windowSize.height / 2
        return CGSize(width: (a * c + b * s) / scale, height: (a * s + b * c) / scale)
    }

    /// Where the photo's centre sits relative to the window centre, in window
    /// units: what the canvas offsets the rotated, scaled image by.
    var windowOffset: CGSize {
        let p = CGPoint(x: -focus.x * scale, y: -focus.y * scale)
            .applying(CGAffineTransform(rotationAngle: theta))
        return CGSize(width: p.x, height: p.y)
    }

    /// The window's four corners taken back into image pixels (from the image centre).
    func windowCornersInImage() -> [CGPoint] {
        let a = windowSize.width / 2, b = windowSize.height / 2
        let back = CGAffineTransform(rotationAngle: -theta)
        return [CGPoint(x: -a, y: -b), CGPoint(x: a, y: -b), CGPoint(x: a, y: b), CGPoint(x: -a, y: b)].map { c in
            let r = c.applying(back)
            return CGPoint(x: focus.x + r.x / scale, y: focus.y + r.y / scale)
        }
    }

    // MARK: - Clamping

    /// Angle and turns in range, scale between covering and the zoom cap, and
    /// the focus pulled in until every window corner is on the photo.
    func normalized() -> CropGeometry {
        var g = self
        g.quarterTurns = ((quarterTurns % 4) + 4) % 4
        g.angle = min(max(angle, -Self.maxAngle), Self.maxAngle)
        g.scale = min(max(scale, g.minScale), g.maxScale)
        g.focus = g.clampedFocus(g.focus)
        return g
    }

    private func clampedFocus(_ point: CGPoint) -> CGPoint {
        let half = halfExtentsInImage(scale: scale)
        let limitX = max(imageSize.width / 2 - half.width, 0)
        let limitY = max(imageSize.height / 2 - half.height, 0)
        return CGPoint(x: min(max(point.x, -limitX), limitX), y: min(max(point.y, -limitY), limitY))
    }

    /// While a drag runs past the edge: the photo follows a share of the
    /// overshoot, so the edge resists instead of stopping dead.
    func rubberBanded() -> CropGeometry {
        var g = self
        g.scale = min(max(scale, minScale), maxScale)
        let clamped = g.clampedFocus(focus)
        g.focus = CGPoint(x: clamped.x + (focus.x - clamped.x) * Self.rubberBandFactor,
                          y: clamped.y + (focus.y - clamped.y) * Self.rubberBandFactor)
        return g
    }

    // MARK: - Edits

    /// The photo follows the finger: a drag of `translation` window units moves
    /// the focus the opposite way, in image pixels. Not clamped.
    func panned(by translation: CGSize) -> CropGeometry {
        var g = self
        let d = CGPoint(x: translation.width, y: translation.height)
            .applying(CGAffineTransform(rotationAngle: -theta))
        g.focus = CGPoint(x: focus.x - d.x / scale, y: focus.y - d.y / scale)
        return g
    }

    /// A pinch about the window centre. Not clamped.
    func zoomed(by factor: CGFloat) -> CropGeometry {
        var g = self
        g.scale = scale * factor
        return g
    }

    /// Spec D6: same focus, the scale raised only if the new angle needs it.
    func withAngle(_ degrees: Double) -> CropGeometry {
        var g = self
        g.angle = degrees
        return g.normalized()
    }

    func withRatio(_ newRatio: CropRatio) -> CropGeometry {
        var g = self
        g.ratio = newRatio
        return g.normalized()
    }

    /// Spec D5: a clockwise quarter turn, the format flipped to its pair.
    func turnedQuarter() -> CropGeometry {
        var g = self
        g.quarterTurns += 1
        g.ratio = ratio.paired
        return g.normalized()
    }
}
