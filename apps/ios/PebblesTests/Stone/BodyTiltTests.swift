import Testing
import simd
@testable import Pebbles

/// The phone held at 60° to the face, then tilted about its own axes: the
/// body-frame increment must read those tilts back whichever way the phone is
/// held. Ported from the Femfolk native card POC.
@Suite("BodyTilt")
struct BodyTiltTests {
    private func rx(_ a: Double) -> simd_double3x3 {
        simd_double3x3(rows: [simd_double3(1, 0, 0), simd_double3(0, cos(a), -sin(a)), simd_double3(0, sin(a), cos(a))])
    }
    private func ry(_ a: Double) -> simd_double3x3 {
        simd_double3x3(rows: [simd_double3(cos(a), 0, sin(a)), simd_double3(0, 1, 0), simd_double3(-sin(a), 0, cos(a))])
    }
    private let deg = Double.pi / 180

    @Test("flat identity reads zero")
    func identity() {
        let a = BodyTilt.angles(reference: matrix_identity_double3x3, current: matrix_identity_double3x3, deviceToWorld: true)
        #expect(a == BodyTilt.Angles(pitch: 0, roll: 0))
    }

    @Test("body tilts read back when the phone is inclined")
    func inclined() {
        let ref = rx(60 * deg)
        let now = ref * rx(5 * deg) * ry(3 * deg)
        let a = BodyTilt.angles(reference: ref, current: now, deviceToWorld: true)
        #expect(abs(a.pitch - 5 * deg) < 0.002)
        #expect(abs(a.roll - 3 * deg) < 0.002)
    }

    @Test("the world-to-device convention gives the same answer")
    func transposed() {
        let ref = rx(60 * deg)
        let now = ref * rx(5 * deg) * ry(3 * deg)
        let a = BodyTilt.angles(reference: ref.transpose, current: now.transpose, deviceToWorld: false)
        #expect(abs(a.pitch - 5 * deg) < 0.002)
        #expect(abs(a.roll - 3 * deg) < 0.002)
    }

    @Test("half a drift halves the tilt and a full drift clears it")
    func drift() {
        let ref = rx(60 * deg)
        let now = ref * ry(6 * deg)
        let half = BodyTilt.drifted(reference: ref, toward: now, fraction: 0.5, deviceToWorld: true)
        #expect(abs(BodyTilt.angles(reference: half, current: now, deviceToWorld: true).roll - 3 * deg) < 0.002)
        let all = BodyTilt.drifted(reference: ref, toward: now, fraction: 1, deviceToWorld: true)
        #expect(abs(BodyTilt.angles(reference: all, current: now, deviceToWorld: true).roll) < 1e-9)
        let halfT = BodyTilt.drifted(reference: ref.transpose, toward: now.transpose, fraction: 0.5, deviceToWorld: false)
        #expect(abs(BodyTilt.angles(reference: halfT, current: now.transpose, deviceToWorld: false).roll - 3 * deg) < 0.002)
    }

    @Test("the convention is read from gravity")
    func convention() {
        let r = rx(60 * deg)
        let upInDevice = r.transpose * simd_double3(0, 0, 1)
        #expect(BodyTilt.convention(of: r, gravity: -upInDevice))
        #expect(!BodyTilt.convention(of: r.transpose, gravity: -upInDevice))
    }

    @Test("a zero lean is the rest light and a lean moves it the other way")
    func tiltedLight() {
        #expect(StoneLight.tilted(by: .zero) == .rest)
        let tilted = StoneLight.tilted(by: Lean(x: 1, y: 0))
        #expect(tilted.direction.dx < StoneLight.rest.direction.dx)
        #expect(tilted.elevationDegrees < StoneLight.rest.elevationDegrees)
    }
}
