import Testing
@testable import Pebbles

@Suite("Device capabilities")
struct DeviceCapabilitiesTests {
    @Test("known Dynamic Island identifiers are recognized")
    func knownIslandModels() {
        #expect(DeviceCapabilities.isDynamicIslandModel("iPhone15,2")) // 14 Pro
        #expect(DeviceCapabilities.isDynamicIslandModel("iPhone16,1")) // 15 Pro
        #expect(DeviceCapabilities.isDynamicIslandModel("iPhone17,3")) // 16
    }

    @Test("non-island and unknown identifiers default to false (capsule)")
    func nonIslandModels() {
        #expect(!DeviceCapabilities.isDynamicIslandModel("iPhone14,7"))  // 14 (no island)
        #expect(!DeviceCapabilities.isDynamicIslandModel("iPhone14,6"))  // SE 3rd gen
        #expect(!DeviceCapabilities.isDynamicIslandModel("iPhone99,9"))  // unknown/future
        #expect(!DeviceCapabilities.isDynamicIslandModel(""))
    }
}
