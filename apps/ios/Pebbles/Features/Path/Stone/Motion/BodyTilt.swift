import CoreMotion
import simd

/// How far the phone has turned about ITS OWN axes since a reference pose,
/// as two small angles: about the left–right axis (x, "pitch") and about the
/// top–bottom axis (y, "roll"). Pure, so it is tested with matrices.
///
/// Why not `CMAttitude.multiply(byInverseOf:)` and its Euler angles: that
/// delta is expressed in the reference frame's axes. Flat on a desk, world
/// and phone axes coincide and it is right; held at 45° to the face, a wrist
/// roll about the phone's long axis comes out as a mix of world pitch and
/// roll, and the light moves diagonally. The body-frame increment
/// Δ = Rrefᵀ · Rnow (for R mapping device → world) is what "how much did I
/// tilt the phone" means whichever way it is held.
///
/// Whether `CMRotationMatrix` maps device → world or world → device is not
/// something the API states, and the two readings differ by a transpose. It
/// is settled per sample from the one vector CoreMotion does state
/// unambiguously, `gravity` in device coordinates. Ported from the Femfolk
/// native card POC.
enum BodyTilt {
    /// Small angles, radians, right-handed about the device's +x and +y.
    struct Angles: Equatable {
        var pitch: Double
        var roll: Double
    }

    static func angles(reference: simd_double3x3, current: simd_double3x3, deviceToWorld: Bool) -> Angles {
        let delta = deviceToWorld
            ? reference.transpose * current
            : reference * current.transpose
        // Axis–angle of a rotation: (Δ32 − Δ23, Δ13 − Δ31, Δ21 − Δ12) = 2 sinθ · axis.
        // simd is column-major: delta[col][row].
        let x = (delta[1][2] - delta[2][1]) / 2
        let y = (delta[2][0] - delta[0][2]) / 2
        return Angles(pitch: asin(max(-1, min(1, x))), roll: asin(max(-1, min(1, y))))
    }

    /// True when the matrix maps device → world, judged against gravity
    /// (device frame, pointing down).
    static func convention(of m: simd_double3x3, gravity g: simd_double3) -> Bool {
        let up = -simd_normalize(g)
        let row = simd_double3(m[0][2], m[1][2], m[2][2])
        let col = m[2]
        return simd_dot(up, row) >= simd_dot(up, col)
    }

    /// The reference moved a fraction `k` of the way toward `current`, along
    /// the shortest rotation between them. Applied every frame with
    /// k = 1 − exp(−dt/τ) it is a high-pass filter on the pose: hold still and
    /// the lean settles to centre on its own; a wrist is far quicker than τ.
    static func drifted(reference: simd_double3x3, toward current: simd_double3x3,
                        fraction k: Double, deviceToWorld: Bool) -> simd_double3x3 {
        let k = max(0, min(1, k))
        if deviceToWorld {
            let delta = reference.transpose * current
            let part = simd_double3x3(simd_slerp(simd_quatd(matrix_identity_double3x3), simd_quatd(delta), k))
            return reference * part
        } else {
            let delta = reference * current.transpose
            let part = simd_double3x3(simd_slerp(simd_quatd(matrix_identity_double3x3), simd_quatd(delta), k))
            return part.transpose * reference
        }
    }

    static func matrix(_ r: CMRotationMatrix) -> simd_double3x3 {
        simd_double3x3(rows: [
            simd_double3(r.m11, r.m12, r.m13),
            simd_double3(r.m21, r.m22, r.m23),
            simd_double3(r.m31, r.m32, r.m33),
        ])
    }
}
