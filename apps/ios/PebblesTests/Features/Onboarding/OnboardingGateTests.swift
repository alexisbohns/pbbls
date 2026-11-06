import Foundation
import Testing
@testable import Pebbles

@Suite("OnboardingGate")
struct OnboardingGateTests {
    @Test func presentsOnlyForAKnownUnseenIncompleteUser() {
        #expect(OnboardingGate.shouldPresent(userId: "u", hasSeen: false, completed: false))
        #expect(!OnboardingGate.shouldPresent(userId: "u", hasSeen: false, completed: nil))
        #expect(!OnboardingGate.shouldPresent(userId: "u", hasSeen: false, completed: true))
        #expect(!OnboardingGate.shouldPresent(userId: "u", hasSeen: true, completed: false))
        #expect(!OnboardingGate.shouldPresent(userId: nil, hasSeen: false, completed: false))
    }

    @Test func pendingWhileTheFlagIsUnknown() {
        #expect(OnboardingGate.isPending(userId: "u", hasSeen: false, completed: nil, readFailed: false))
        #expect(!OnboardingGate.isPending(userId: "u", hasSeen: true, completed: nil, readFailed: false))
        #expect(!OnboardingGate.isPending(userId: "u", hasSeen: false, completed: false, readFailed: false))
        #expect(!OnboardingGate.isPending(userId: "u", hasSeen: false, completed: nil, readFailed: true))
        #expect(!OnboardingGate.isPending(userId: nil, hasSeen: false, completed: nil, readFailed: false))
    }
}

@Suite("OnboardingGateModel")
@MainActor
struct OnboardingGateModelTests {
    private struct Boom: Error {}

    private func defaults() throws -> UserDefaults {
        let name = "onboarding-gate-\(UUID().uuidString)"
        let suite = try #require(UserDefaults(suiteName: name))
        suite.removePersistentDomain(forName: name)
        return suite
    }

    private func model(_ service: FakeOnboardingService, seen: Bool = false) throws -> OnboardingGateModel {
        let d = try defaults()
        if seen { d.set(true, forKey: OnboardingGateModel.hasSeenKey) }
        let m = OnboardingGateModel(defaults: d)
        m.service = service
        return m
    }

    @Test func readsOncePerUser() async throws {
        let service = FakeOnboardingService(completed: false)
        let m = try model(service)
        m.start(userId: "a")
        #expect(m.isPending)
        await m.work?.value
        m.start(userId: "a")
        await m.work?.value
        #expect(service.completedCalls == 1)
        #expect(m.shouldPresent)
        #expect(!m.isPending)
    }

    @Test func aNewUserForgetsTheLastFlag() async throws {
        let service = FakeOnboardingService(completed: false)
        let m = try model(service)
        m.start(userId: "a")
        await m.work?.value
        #expect(m.shouldPresent)
        m.start(userId: "b")
        #expect(!m.shouldPresent)
        #expect(m.isPending)
        await m.work?.value
        #expect(service.completedCalls == 2)
    }

    @Test func hasSeenSkipsTheRead() throws {
        let service = FakeOnboardingService()
        let m = try model(service, seen: true)
        m.start(userId: "a")
        #expect(service.completedCalls == 0)
        #expect(!m.isPending)
        #expect(!m.shouldPresent)
    }

    @Test func aFailedReadIsNeitherPendingNorPresenting() async throws {
        let service = FakeOnboardingService()
        service.completedFailure = Boom()
        let m = try model(service)
        m.start(userId: "a")
        await m.work?.value
        #expect(m.readFailed)
        #expect(!m.isPending)
        #expect(!m.shouldPresent)
    }

    @Test func finishMarksSeenAndCompleted() async throws {
        let d = try defaults()
        let service = FakeOnboardingService(completed: false)
        let m = OnboardingGateModel(defaults: d)
        m.service = service
        m.start(userId: "a")
        await m.work?.value
        m.finish()
        #expect(m.hasSeen)
        #expect(m.completed == true)
        #expect(!m.shouldPresent)
        #expect(d.bool(forKey: OnboardingGateModel.hasSeenKey))
    }

    @Test func signingOutClearsEverything() async throws {
        let service = FakeOnboardingService(completed: false)
        let m = try model(service)
        m.start(userId: "a")
        await m.work?.value
        m.start(userId: nil)
        #expect(m.completed == nil)
        #expect(!m.readFailed)
        #expect(!m.shouldPresent)
        #expect(!m.isPending)
    }

    // MARK: superseded reads

    /// Lets the model's task reach the service and suspend there.
    private func waitForCalls(_ n: Int, _ service: FakeOnboardingService) async {
        for _ in 0..<2000 where service.completedCalls < n { await Task.yield() }
    }

    @Test func aReadLandingForAPreviousUserIsIgnored() async throws {
        let service = FakeOnboardingService()
        let first = Latch()
        let second = Latch()
        service.onCompleted = { call in
            await (call == 1 ? first : second).wait()
            return call == 1
        }
        let m = try model(service)
        m.start(userId: "a")
        await waitForCalls(1, service)
        let read1 = m.work
        m.start(userId: "b")
        await waitForCalls(2, service)
        first.open()
        await read1?.value
        #expect(m.completed == nil)
        #expect(m.isPending)
        second.open()
        await m.work?.value
        #expect(m.completed == false)
    }

    @Test func signOutAndBackInKeepsTheSecondReadsAnswer() async throws {
        let service = FakeOnboardingService()
        let first = Latch()
        let second = Latch()
        service.onCompleted = { call in
            if call == 1 {
                await first.wait()
                throw URLError(.cancelled)
            }
            await second.wait()
            return false
        }
        let m = try model(service)
        m.start(userId: "a")
        await waitForCalls(1, service)
        let read1 = m.work
        m.start(userId: nil)
        m.start(userId: "a")
        await waitForCalls(2, service)
        first.open()
        await read1?.value
        #expect(!m.readFailed)
        #expect(m.isPending)
        second.open()
        await m.work?.value
        #expect(!m.readFailed)
        #expect(m.shouldPresent)
    }

    @Test func aStaleSuccessNeverOverwritesTheLatestRead() async throws {
        let service = FakeOnboardingService()
        let first = Latch()
        let second = Latch()
        service.onCompleted = { call in
            await (call == 1 ? first : second).wait()
            return call == 1
        }
        let m = try model(service)
        m.start(userId: "a")
        await waitForCalls(1, service)
        let read1 = m.work
        m.start(userId: nil)
        m.start(userId: "a")
        await waitForCalls(2, service)
        second.open()
        await m.work?.value
        first.open()
        await read1?.value
        #expect(m.completed == false)
        #expect(m.shouldPresent)
    }

    @Test func aThrownCancelledReadLeavesReadFailedFalse() async throws {
        let service = FakeOnboardingService()
        let first = Latch()
        let second = Latch()
        // Only the first call is cancelled. A hook shared by both calls would let the live
        // second read throw too, and a live read that throws is a real failure.
        service.onCompleted = { call in
            if call == 1 {
                await first.wait()
                throw CancellationError()
            }
            await second.wait()
            return false
        }
        let m = try model(service)
        m.start(userId: "a")
        await waitForCalls(1, service)
        let read1 = m.work
        m.start(userId: nil)
        m.start(userId: "a")
        await waitForCalls(2, service)
        first.open()
        await read1?.value
        #expect(!m.readFailed)
        #expect(m.isPending)
        second.open()
        await m.work?.value
        #expect(!m.readFailed)
        #expect(m.shouldPresent)
    }

    @Test func finishDuringAReadStaysFinished() async throws {
        let service = FakeOnboardingService()
        let gate = Latch()
        service.onCompleted = { _ in
            await gate.wait()
            return false
        }
        let m = try model(service)
        m.start(userId: "a")
        await waitForCalls(1, service)
        m.finish()
        gate.open()
        await m.work?.value
        #expect(m.completed == true)
        #expect(m.hasSeen)
        #expect(!m.shouldPresent)
    }

    @Test func startWithoutAServiceFailsTheRead() throws {
        let m = OnboardingGateModel(defaults: try defaults())
        m.start(userId: "a")
        #expect(m.readFailed)
        #expect(!m.isPending)
        #expect(!m.shouldPresent)
    }
}
