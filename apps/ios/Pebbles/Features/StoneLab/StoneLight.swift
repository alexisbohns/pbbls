#if DEBUG
import CoreGraphics
import simd

/// Where the light is, in the stone's plane. `direction` points from the
/// stone toward the light in screen coordinates (y down), unit length;
/// `elevationDegrees` lifts it off the plane.
struct StoneLight: Equatable {
    var direction: CGVector
    var elevationDegrees: Double

    /// Top-left, 55° up: the rest pose the page springs back to.
    static let rest = StoneLight(direction: CGVector(dx: -sqrt(0.5), dy: -sqrt(0.5)), elevationDegrees: 55)

    /// A light in the direction of `offset` from the stone's centre. A zero
    /// offset keeps the rest direction rather than producing NaN.
    init(pointingTo offset: CGVector, elevationDegrees: Double) {
        let length = hypot(offset.dx, offset.dy)
        self.direction = length > 0.001
            ? CGVector(dx: offset.dx / length, dy: offset.dy / length)
            : StoneLight.rest.direction
        self.elevationDegrees = elevationDegrees
    }

    /// A non-unit `direction` is normalised; a zero one keeps the rest
    /// direction rather than producing NaN.
    init(direction: CGVector, elevationDegrees: Double) {
        let length = hypot(direction.dx, direction.dy)
        self.direction = length > 0.001
            ? CGVector(dx: direction.dx / length, dy: direction.dy / length)
            : StoneLight.rest.direction
        self.elevationDegrees = elevationDegrees
    }

    /// Unit vector for the shader: xy in the plane, z up out of the screen.
    var vector: SIMD3<Float> {
        let elevation = elevationDegrees * .pi / 180
        return SIMD3<Float>(
            Float(direction.dx * cos(elevation)),
            Float(direction.dy * cos(elevation)),
            Float(sin(elevation))
        )
    }

    /// The carving lip offset toward the light, in points.
    func lipOffset(width: Double) -> CGSize {
        CGSize(width: direction.dx * width, height: direction.dy * width)
    }

    /// Where the sheen pool sits: pulled toward the light from the centre.
    var poolCenter: UnitPointValue {
        UnitPointValue(x: 0.5 + direction.dx * 0.35, y: 0.5 + direction.dy * 0.35)
    }

    struct UnitPointValue: Equatable {
        let x: Double
        let y: Double
    }
}
#endif
