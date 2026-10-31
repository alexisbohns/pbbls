import Foundation
import Testing
@testable import Pebbles

/// `YouServicing` for `YouModel`'s tests: fixed answers, the calls counted.
final class FakeYouService: YouServicing, @unchecked Sendable {
    struct Failure: Error {}
    private let lock = NSLock()
    private let profileAnswer: Result<YouProfile, Error>
    private let strokesAnswer: Result<[GlyphStroke], Error>
    private var profileCallCount = 0

    init(profile: Result<YouProfile, Error>, strokes: Result<[GlyphStroke], Error> = .success([])) {
        profileAnswer = profile
        strokesAnswer = strokes
    }

    var profileCalls: Int { lock.withLock { profileCallCount } }

    func profile() async throws -> YouProfile {
        try lock.withLock {
            profileCallCount += 1
            return try profileAnswer.get()
        }
    }

    func glyphStrokes(id: UUID) async throws -> [GlyphStroke] { try strokesAnswer.get() }
}

@Suite("You model") @MainActor
struct YouModelTests {
    private static let strokes = [GlyphStroke(d: "M40,40 L160,160", width: 6)]

    @Test func loadsTheNameTheDateAndTheGlyph() async {
        let model = YouModel()
        model.service = FakeYouService(profile: .success(YouFixture.profile), strokes: .success(Self.strokes))
        await model.loadProfile()
        #expect(model.profile?.displayName == "Alexis")
        #expect(model.profile?.createdAt == YouFixture.profile.createdAt)
        #expect(model.glyphStrokes == Self.strokes)
    }

    @Test func aFailedReadLeavesThemEmpty() async {
        let model = YouModel()
        model.service = FakeYouService(profile: .failure(FakeYouService.Failure()))
        await model.loadProfile()
        #expect(model.profile == nil && model.glyphStrokes == nil)
        #expect(model.hasLoadedProfile)
    }

    @Test func aSecondLoadIsANoOp() async {
        let model = YouModel()
        let fake = FakeYouService(profile: .success(YouFixture.profile))
        model.service = fake
        await model.loadProfile()
        await model.loadProfile()
        #expect(fake.profileCalls == 1)
    }

    @Test func settingsApplyWithoutARead() async {
        let model = YouModel()
        model.service = FakeYouService(profile: .success(YouFixture.profile))
        await model.loadProfile()
        model.applySettings(name: "Alex", glyph: (UUID(), Self.strokes), handle: nil, isPublic: false)
        #expect(model.profile?.displayName == "Alex" && model.glyphStrokes == Self.strokes)
        #expect(model.profile?.publicProfile == false)
    }
}
