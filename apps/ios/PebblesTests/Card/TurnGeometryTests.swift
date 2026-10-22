import CoreGraphics
import SwiftUI
import Testing
@testable import Pebbles

/// #1146 §Turn: `(t > 0.5 ? t − 1 : t) × 180°`, negative in LTR, mirrored in
/// RTL, so text is never mirrored; the eye 864 pt away; the fit keeps the near
/// edge inside the shell (decision 1).
@Suite("TurnGeometry")
struct TurnGeometryTests {
    private func degrees(_ t: Double, rtl: Bool = false) -> Double {
        TurnGeometry.angle(progress: t, rightToLeft: rtl).degrees
    }

    @Test("the angle at 0, 0.49, 0.51 and 1")
    func angle() {
        #expect(abs(degrees(0)) < 1e-9)
        #expect(abs(degrees(0.49) + 88.2) < 1e-9)
        #expect(abs(degrees(0.51) - 88.2) < 1e-9)
        #expect(abs(degrees(1)) < 1e-9)
    }

    @Test("right to left mirrors the angle")
    func rtl() {
        #expect(abs(degrees(0.25, rtl: true) - 45) < 1e-9)
        #expect(abs(degrees(0.25) + 45) < 1e-9)
    }

    @Test("the perspective puts the eye 864 pt from the larger side")
    func perspective() {
        #expect(abs(TurnGeometry.perspective(width: 361, height: 640) - 640 / 864) < 1e-9)
        #expect(abs(TurnGeometry.perspective(width: 500, height: 300) - 500 / 864) < 1e-9)
    }

    @Test("the fit is 1 at rest and smallest edge-on")
    func fit() {
        #expect(abs(TurnGeometry.fitScale(progress: 0, width: 361) - 1) < 1e-9)
        #expect(abs(TurnGeometry.fitScale(progress: 1, width: 361) - 1) < 1e-9)
        let edgeOn = TurnGeometry.fitScale(progress: 0.5, width: 361)
        #expect(abs(edgeOn - (1 - 180.5 / 864)) < 1e-9)
        #expect(edgeOn < TurnGeometry.fitScale(progress: 0.3, width: 361))
    }
}
