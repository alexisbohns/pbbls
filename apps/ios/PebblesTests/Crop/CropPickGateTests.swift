import Foundation
import Testing
@testable import Pebbles

@Suite("CropPickGate")
struct CropPickGateTests {
    @Test("a pick counts as coming from its tap")
    func beginActivates() {
        var gate = CropPickGate()
        #expect(!gate.isActive)
        _ = gate.begin()
        #expect(gate.isActive)
        #expect(gate.isLoading)
    }

    @Test("a load that finishes while current opens the editor and stays active")
    func currentLoadOpens() {
        var gate = CropPickGate()
        let token = gate.begin()
        let accepted = gate.finishLoading(token, opened: true)
        #expect(accepted)
        #expect(!gate.isLoading)
        #expect(gate.isActive) // the editor is open: the photo still counts as coming
    }

    @Test("a load that returns nothing releases the gate")
    func unusableLoadReleases() {
        var gate = CropPickGate()
        let token = gate.begin()
        let accepted = gate.finishLoading(token, opened: false)
        #expect(accepted)
        #expect(!gate.isActive)
    }

    @Test("a later pick supersedes an earlier one even when the earlier one loads first")
    func laterPickWins() {
        var gate = CropPickGate()
        let first = gate.begin()
        let second = gate.begin()
        let firstAccepted = gate.finishLoading(first, opened: true)
        #expect(!firstAccepted)
        #expect(gate.isLoading) // the second is still loading
        let secondAccepted = gate.finishLoading(second, opened: true)
        #expect(secondAccepted)
    }

    @Test("a stale unusable load does not release a newer pick")
    func staleFailureKeepsNewer() {
        var gate = CropPickGate()
        let first = gate.begin()
        let second = gate.begin()
        let accepted = gate.finishLoading(first, opened: false)
        #expect(!accepted)
        #expect(gate.token == second)
        #expect(gate.isActive)
    }

    @Test("a load landing after clear is dropped")
    func clearDropsLoad() {
        var gate = CropPickGate()
        let token = gate.begin()
        gate.clear()
        #expect(!gate.isActive)
        let accepted = gate.finishLoading(token, opened: true)
        #expect(!accepted)
        #expect(!gate.isActive)
    }

    @Test("release ends the pick it names and leaves a newer one alone")
    func releaseIsTokenScoped() {
        var gate = CropPickGate()
        let first = gate.begin()
        let accepted = gate.finishLoading(first, opened: true)
        #expect(accepted)
        let second = gate.begin()
        gate.release(first) // the first pick's upload finishing late
        #expect(gate.token == second)
        gate.release(second)
        #expect(!gate.isActive)
    }
}
