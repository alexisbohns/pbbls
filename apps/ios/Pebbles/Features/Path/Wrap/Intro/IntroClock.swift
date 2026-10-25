import CoreGraphics
import Foundation
import Observation

/// What a month page needs to play its intro: the launch's ledger and the haptics (nil: silent).
struct IntroContext {
    let ledger: IntroLedger
    let haptics: IntroHapticPlaying?
}

/// Where a sequence sits in its page's viewport (#1241 §Lifecycle).
enum IntroViewport {
    enum Place: Equatable {
        case entered, between, out
    }

    /// A sequence enters once its top passes this share of the viewport's height.
    static let entryLine: CGFloat = 0.85

    static func place(_ frame: CGRect, viewport: CGFloat) -> Place {
        if frame.maxY <= 0 || frame.minY >= viewport { return .out }
        return frame.minY < viewport * entryLine ? .entered : .between
    }
}

/// Whether the intro plays at all (#1241 §Accessibility): not under Reduce
/// Motion, not while VoiceOver runs, where hidden and moving parts would fight
/// its focus.
enum IntroPolicy {
    static func plays(reduceMotion: Bool, voiceOver: Bool) -> Bool {
        !reduceMotion && !voiceOver
    }
}

/// A month page's intro (#1241 D1, D3, D4): which sequence plays, which waits,
/// which has landed. One voice at a time: a sequence in view and still waiting
/// plays when nothing else does, in page order, and only while the page is
/// active. A sequence plays once per launch (the ledger records it as it
/// starts). Views read `reveal(_:at:)`; the page drives `isActive` and taps
/// `skip()`; each sequence reports where it is.
@Observable @MainActor
final class IntroClock {
    enum Phase: Equatable {
        case waiting
        case playing(Date)
        case settled
    }

    /// A sequence starts this far ahead, so its haptic player and its frames begin together.
    static let lead: TimeInterval = 0.05

    let month: CalendarMonth
    /// A reference's instant: every playing sequence is drawn at it, and nothing moves.
    @ObservationIgnored let frozenAt: Date?

    private var phases: [IntroSequence: Phase] = [:]
    @ObservationIgnored private var keyed: [IntroSequence: [IntroKey: IntroBeat]] = [:]
    @ObservationIgnored private var latest: [IntroSequence: [IntroBeat]] = [:]
    @ObservationIgnored private var ends: [IntroSequence: Date] = [:]
    @ObservationIgnored private var inView: Set<IntroSequence> = []
    @ObservationIgnored private let order: [IntroSequence]
    @ObservationIgnored private let ledger: IntroLedger
    @ObservationIgnored private let haptics: IntroHapticPlaying?
    @ObservationIgnored private let now: () -> Date
    @ObservationIgnored private let schedules: Bool
    @ObservationIgnored private var ending: Task<Void, Never>?

    /// The page is focused, the app active, and the intro allowed (`IntroPolicy`). Off lands what plays.
    var isActive = false {
        didSet {
            guard isActive != oldValue else { return }
            if isActive {
                haptics?.prepare()
                startNext()
            } else {
                settleAll()
            }
        }
    }

    /// `schedules: false` leaves the landing to `advance(to:)`, for the tests.
    convenience init(month: CalendarMonth, order: [IntroSequence], ledger: IntroLedger,
                     haptics: IntroHapticPlaying?, now: @escaping () -> Date = Date.init, schedules: Bool = true) {
        self.init(month: month, order: order, ledger: ledger, haptics: haptics, now: now, schedules: schedules,
                  frozenAt: nil)
    }

    private init(month: CalendarMonth, order: [IntroSequence], ledger: IntroLedger, haptics: IntroHapticPlaying?,
                 now: @escaping () -> Date, schedules: Bool, frozenAt: Date?) {
        self.month = month
        self.order = order
        self.ledger = ledger
        self.haptics = haptics
        self.now = now
        self.schedules = schedules
        self.frozenAt = frozenAt
    }

    /// The cover, then the sections in the server's order.
    static func order(_ wrap: MonthWrap) -> [IntroSequence] {
        [.cover] + wrap.sections.map { .section($0) }
    }

    /// A reference's clock: `sequence` playing, drawn `elapsed` seconds in.
    static func frozen(month: CalendarMonth, sequence: IntroSequence, beats: [IntroBeat],
                       elapsed: TimeInterval) -> IntroClock {
        let start = Date(timeIntervalSinceReferenceDate: 0)
        let clock = IntroClock(month: month, order: [sequence], ledger: IntroLedger(), haptics: nil,
                               now: { start }, schedules: false, frozenAt: start.addingTimeInterval(elapsed))
        clock.keyed[sequence] = index(beats)
        clock.phases[sequence] = .playing(start)
        return clock
    }

    func phase(_ sequence: IntroSequence) -> Phase {
        phases[sequence] ?? (ledger.contains(month, sequence) ? .settled : .waiting)
    }

    func isPlaying(_ sequence: IntroSequence) -> Bool {
        if case .playing = phase(sequence) { return true }
        return false
    }

    /// Each key's linear progress in `sequence` at `date`: nothing before the
    /// sequence's turn; while it plays, its beat's progress (a key the beats
    /// don't name has landed); everything once it has landed.
    func reveal(_ sequence: IntroSequence, at date: Date) -> IntroReveal {
        switch phase(sequence) {
        case .settled:
            return .settled
        case .waiting:
            return .hidden
        case let .playing(start):
            let elapsed = date.timeIntervalSince(start)
            let beats = keyed[sequence] ?? [:]
            return IntroReveal { key in
                guard let beat = beats[key] else { return 1 }
                guard beat.duration > 0 else { return elapsed >= beat.at ? 1 : 0 }
                return IntroMotion.clamp((elapsed - beat.at) / beat.duration)
            }
        }
    }

    /// A sequence's frame in its page: in view, it waits its turn; wholly out, it leaves.
    func report(_ sequence: IntroSequence, beats: [IntroBeat], frame: CGRect, viewport: CGFloat) {
        guard frozenAt == nil, viewport > 0 else { return }
        switch IntroViewport.place(frame, viewport: viewport) {
        case .entered: enter(sequence, beats: beats)
        case .out: leave(sequence)
        case .between: break
        }
    }

    func enter(_ sequence: IntroSequence, beats: [IntroBeat]) {
        latest[sequence] = beats
        guard inView.insert(sequence).inserted else { return }
        startNext()
    }

    func leave(_ sequence: IntroSequence) {
        guard inView.remove(sequence) != nil else { return }
        if isPlaying(sequence) { land(sequence) }
    }

    /// Lands the playing sequence once `date` is past its last beat, and starts the next.
    func advance(to date: Date) {
        for (sequence, end) in ends where date >= end {
            land(sequence)
        }
    }

    /// A tap: what plays and what waits in view land now, and count as played.
    func skip() {
        for sequence in inView where phase(sequence) == .waiting {
            ledger.record(month, sequence)
            phases[sequence] = .settled
        }
        settleAll()
    }

    /// Focus lost or the app inactive: what plays lands; what waits keeps waiting, unrecorded.
    func settleAll() {
        ending?.cancel()
        ending = nil
        haptics?.stopAll()
        for sequence in ends.keys {
            phases[sequence] = .settled
        }
        ends = [:]
    }

    /// The playing sequence has landed (its time came, or it left the viewport): the next may start.
    private func land(_ sequence: IntroSequence) {
        guard ends.removeValue(forKey: sequence) != nil else { return }
        ending?.cancel()
        ending = nil
        haptics?.stop(sequence)
        phases[sequence] = .settled
        startNext()
    }

    /// Starts the first sequence in view and still waiting, in page order, when the page may play and nothing does.
    private func startNext() {
        guard isActive, ends.isEmpty else { return }
        let rank = { (sequence: IntroSequence) in self.order.firstIndex(of: sequence) ?? self.order.count }
        guard let next = inView.filter({ phase($0) == .waiting }).min(by: { rank($0) < rank($1) }) else { return }
        start(next, beats: latest[next] ?? [])
    }

    private func start(_ sequence: IntroSequence, beats: [IntroBeat]) {
        let begin = now().addingTimeInterval(Self.lead)
        let end = begin.addingTimeInterval(IntroScore.duration(beats))
        keyed[sequence] = Self.index(beats)
        ends[sequence] = end
        phases[sequence] = .playing(begin)
        ledger.record(month, sequence)
        haptics?.play(sequence, beats: beats, after: Self.lead)
        guard schedules else { return }
        let wait = end.timeIntervalSince(now())
        ending = Task { [weak self] in
            do {
                try await Task.sleep(for: .seconds(wait))
            } catch {
                return // Cancelled: the sequence landed some other way.
            }
            self?.advance(to: end)
        }
    }

    private static func index(_ beats: [IntroBeat]) -> [IntroKey: IntroBeat] {
        Dictionary(beats.map { ($0.key, $0) }, uniquingKeysWith: { first, _ in first })
    }
}
