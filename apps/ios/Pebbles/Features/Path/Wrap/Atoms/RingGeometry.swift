import CoreGraphics
import Foundation

/// Positions on a ring (#1226; Android: `RadialLayout`): the month's circle of
/// day dots (31 slots, a 1.5-slot gap), the garland (32 slots, the tie in the
/// gap at the foot), the hours dial (`continuous`, noon on top) and the gem's
/// seven positions turned by its rotation.
///
/// The circle is cut into `slots + gapSlots` equal steps. Slot 0 sits half the
/// gap past `startDegrees`, so the gap is centred on it; slots may be
/// fractional. Angles are in degrees, 0 at three o'clock, growing clockwise on
/// screen (y down), as `GraphicsContext` and `Path` measure them.
struct RingGeometry: Equatable {
    let slots: Int
    let startDegrees: Double
    let gapSlots: Double
    let clockwise: Bool
    private let origin: Double

    init(slots: Int, startDegrees: Double = -90, gapSlots: Double = 0, clockwise: Bool = true) {
        self.init(slots: slots, startDegrees: startDegrees, gapSlots: gapSlots, clockwise: clockwise, origin: 0)
    }

    private init(slots: Int, startDegrees: Double, gapSlots: Double, clockwise: Bool, origin: Double) {
        precondition(slots > 0, "a ring needs at least one slot")
        self.slots = slots
        self.startDegrees = startDegrees
        self.gapSlots = gapSlots
        self.clockwise = clockwise
        self.origin = origin
    }

    /// A value on a closed scale (`period` units around), with `top` at twelve
    /// o'clock: the dial is `continuous(24, top: 12)`.
    static func continuous(_ period: Int, top: Double = 0) -> RingGeometry {
        RingGeometry(slots: period, startDegrees: -90, gapSlots: 0, clockwise: true, origin: top)
    }

    var stepDegrees: Double { 360 / (Double(slots) + gapSlots) }

    func degrees(_ slot: Double) -> Double {
        let turn = (gapSlots / 2 + slot - origin) * stepDegrees
        return startDegrees + (clockwise ? turn : -turn)
    }

    func point(_ slot: Double, center: CGPoint, radius: CGFloat) -> CGPoint {
        let radians = degrees(slot) * .pi / 180
        return CGPoint(x: center.x + radius * CGFloat(cos(radians)), y: center.y + radius * CGFloat(sin(radians)))
    }
}
