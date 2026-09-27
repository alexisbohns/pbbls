import CoreMotion
import Observation
import os
import simd

/// `CMMotionManager` at 60 Hz feeding `LeanMapper`. `lean` is what views
/// read; `available` is false in the simulator. Main-actor isolated: updates
/// are delivered on the main queue and SwiftUI reads the state there.
///
/// The reference pose is a rotation matrix, and each sample is read as the
/// phone's turn about its own axes since that pose (`BodyTilt`). The
/// reference is latched after a short warm-up (sensor fusion needs a moment),
/// then drifts toward the current pose with a four-second time constant, so
/// however the phone is held becomes "flat" by itself. `rezero()` latches at
/// once. Ported from the Femfolk native card POC.
@MainActor
@Observable
final class StoneMotionSource {
    private(set) var lean = Lean.zero
    private(set) var available = false

    static let warmup: TimeInterval = 0.3
    static let driftTau: TimeInterval = 4
    /// Below this movement, a thousandth of the full travel, nothing is
    /// published and a still phone rests.
    static let deadBand = 0.001

    @ObservationIgnored private let manager = CMMotionManager()
    @ObservationIgnored private var mapper = LeanMapper()
    @ObservationIgnored private var reference: simd_double3x3?
    @ObservationIgnored private var referenceDeviceToWorld = true
    @ObservationIgnored private var readyAt: TimeInterval?
    @ObservationIgnored private var lastTimestamp: TimeInterval?
    @ObservationIgnored private let logger = Logger(subsystem: "app.pbbls.ios", category: "stone-motion")

    func start() {
        guard manager.isDeviceMotionAvailable else {
            available = false
            logger.info("device motion unavailable; the stone light stays at rest")
            return
        }
        guard !manager.isDeviceMotionActive else { return }
        available = true
        readyAt = nil
        manager.deviceMotionUpdateInterval = 1.0 / 60
        manager.startDeviceMotionUpdates(using: .xArbitraryZVertical, to: .main) { [weak self] motion, error in
            if let error {
                Logger(subsystem: "app.pbbls.ios", category: "stone-motion")
                    .error("device motion update failed: \(error.localizedDescription, privacy: .public)")
            }
            guard let motion else { return }
            MainActor.assumeIsolated { self?.consume(motion) }
        }
    }

    /// Stopping forgets the reference: the next appearance re-latches.
    func stop() {
        manager.stopDeviceMotionUpdates()
        lastTimestamp = nil
        reference = nil
        readyAt = nil
        lean = .zero
    }

    /// Wherever the phone is now becomes "flat".
    func rezero() {
        reference = nil
    }

    private func consume(_ motion: CMDeviceMotion) {
        let ts = motion.timestamp
        if readyAt == nil { readyAt = ts + Self.warmup }
        let current = BodyTilt.matrix(motion.attitude.rotationMatrix)
        let gravity = simd_double3(motion.gravity.x, motion.gravity.y, motion.gravity.z)
        guard let reference else {
            if let readyAt, ts >= readyAt {
                self.reference = current
                referenceDeviceToWorld = BodyTilt.convention(of: current, gravity: gravity)
            }
            return
        }
        let dt = lastTimestamp.map { ts - $0 } ?? (1.0 / 60)
        lastTimestamp = ts
        let tilt = BodyTilt.angles(reference: reference, current: current, deviceToWorld: referenceDeviceToWorld)
        let next = mapper.step(pitch: tilt.pitch, roll: tilt.roll, dt: dt)
        if next.moved(from: lean, atLeast: Self.deadBand) { lean = next }
        let k = 1 - exp(-min(max(dt, 0), LeanMapper.maxFrame) / Self.driftTau)
        self.reference = BodyTilt.drifted(reference: reference, toward: current, fraction: k,
                                          deviceToWorld: referenceDeviceToWorld)
    }
}
