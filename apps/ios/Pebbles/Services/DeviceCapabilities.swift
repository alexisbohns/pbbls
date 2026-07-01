import Foundation

/// Static device-capability checks.
///
/// There is no public `hasDynamicIsland` API, so this is a model-identifier
/// allowlist. MAINTENANCE POINT: add new Dynamic Island hardware here as it
/// ships. Unknown/future identifiers deliberately default to `false` so the
/// worst case is a brand-new DI phone showing the in-app capsule (always-
/// visible feedback) instead of the Live Activity — graceful degradation,
/// never a silent miss.
enum DeviceCapabilities {
    /// Model identifiers (e.g. "iPhone15,2") with a Dynamic Island.
    static let dynamicIslandIdentifiers: Set<String> = [
        "iPhone15,2", "iPhone15,3",   // iPhone 14 Pro / Pro Max
        "iPhone15,4", "iPhone15,5",   // iPhone 15 / 15 Plus
        "iPhone16,1", "iPhone16,2",   // iPhone 15 Pro / Pro Max
        "iPhone17,3", "iPhone17,4",   // iPhone 16 / 16 Plus
        "iPhone17,1", "iPhone17,2"    // iPhone 16 Pro / Pro Max
    ]

    /// Pure, testable membership check.
    static func isDynamicIslandModel(_ identifier: String) -> Bool {
        dynamicIslandIdentifiers.contains(identifier)
    }

    /// This device's model identifier. On the simulator, reads
    /// `SIMULATOR_MODEL_IDENTIFIER`; on device, reads `uname().machine`.
    static var currentModelIdentifier: String {
        if let sim = ProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] {
            return sim
        }
        var systemInfo = utsname()
        uname(&systemInfo)
        let machine = withUnsafeBytes(of: &systemInfo.machine) { raw -> String in
            let bytes = Array(raw.prefix { $0 != 0 })
            return String(bytes: bytes, encoding: .utf8) ?? ""
        }
        return machine
    }

    static var hasDynamicIsland: Bool {
        isDynamicIslandModel(currentModelIdentifier)
    }
}
