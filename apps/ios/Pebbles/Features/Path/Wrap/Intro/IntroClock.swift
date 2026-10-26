import Foundation
import Observation

/// What a month page needs to play its intro: the launch's ledger, the haptics (nil: silent) and the tempo.
struct IntroContext {
    let ledger: IntroLedger
    let haptics: IntroHapticPlaying?
    var tempo = IntroTempo.standard
}

/// Whether the intro plays at all (#1241 §Accessibility, D11): not under
/// Reduce Motion, not while VoiceOver runs, where hidden and moving parts
/// would fight its focus, and not at an accessibility text size, where a
/// section scaled to fit its card would read too small: the month is then
/// the vertical page, settled.
enum IntroPolicy {
    static func plays(reduceMotion: Bool, voiceOver: Bool, largeText: Bool) -> Bool {
        !reduceMotion && !voiceOver && !largeText
    }
}

/// A month page's intro (#1241 D1, D3, D4, D11): which sequence plays, which
/// waits, which has landed. One voice at a time: a sequence in view and still
/// waiting plays when nothing else does, in page order, and only while the
/// page is active. A sequence plays once per launch (the ledger records it as
/// it starts). Views read `reveal(_:at:)`; the page drives `isActive`, taps
/// `skip()` and keeps `order` to its sections. Each sequence's host registers
/// a closure for its beats, asked for only as it starts; the deck says which
/// card is on top (`show(_:)`), and that card alone is in view.
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
    /// How much slower than its score each sequence plays (D9); a frozen clock draws its beats as given.
    @ObservationIgnored let tempo: IntroTempo

    private var phases: [IntroSequence: Phase] = [:]
    @ObservationIgnored private var keyed: [IntroSequence: [IntroKey: IntroBeat]] = [:]
    /// Each sequence's beats, from the latest wrap its host drew.
    @ObservationIgnored private var latest: [IntroSequence: () -> [IntroBeat]] = [:]
    @ObservationIgnored private var ends: [IntroSequence: Date] = [:]
    @ObservationIgnored private var inView: Set<IntroSequence> = []
    /// The page's sequences in order (`order(_:)`): the page sets it again when its sections change.
    @ObservationIgnored var order: [IntroSequence]
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
                // Only a page with something still to play wakes the engine.
                if order.contains(where: { !ledger.contains(month, $0) }) { haptics?.prepare() }
                startNext()
            } else {
                settleAll()
            }
        }
    }

    /// `schedules: false` leaves the landing to `advance(to:)`, for the tests.
    convenience init(month: CalendarMonth, order: [IntroSequence], ledger: IntroLedger,
                     haptics: IntroHapticPlaying?, tempo: IntroTempo = .standard,
                     now: @escaping () -> Date = Date.init, schedules: Bool = true) {
        self.init(month: month, order: order, ledger: ledger, haptics: haptics, tempo: tempo, now: now,
                  schedules: schedules, frozenAt: nil)
    }

    private init(month: CalendarMonth, order: [IntroSequence], ledger: IntroLedger, haptics: IntroHapticPlaying?,
                 tempo: IntroTempo, now: @escaping () -> Date, schedules: Bool, frozenAt: Date?) {
        self.month = month
        self.order = order
        self.ledger = ledger
        self.haptics = haptics
        self.tempo = tempo
        self.now = now
        self.schedules = schedules
        self.frozenAt = frozenAt
    }

    /// The cover, then the sections in the server's order.
    static func order(_ wrap: MonthWrap) -> [IntroSequence] {
        [.cover] + wrap.sections.map { .section($0) }
    }

    /// A reference's clock: `sequence` playing, drawn `elapsed` seconds in, its beats unscaled.
    static func frozen(month: CalendarMonth, sequence: IntroSequence, beats: [IntroBeat],
                       elapsed: TimeInterval) -> IntroClock {
        let start = Date(timeIntervalSinceReferenceDate: 0)
        let clock = IntroClock(month: month, order: [sequence], ledger: IntroLedger(), haptics: nil,
                               tempo: .standard, now: { start }, schedules: false,
                               frozenAt: start.addingTimeInterval(elapsed))
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

    /// A sequence's beats, from the latest wrap its host drew, asked for only
    /// as it starts. A sequence on top and waiting for them starts now.
    func register(_ sequence: IntroSequence, beats: @escaping () -> [IntroBeat]) {
        guard frozenAt == nil else { return }
        latest[sequence] = beats
        startNext()
    }

    /// The deck's top card (D11): every other sequence leaves the view, one
    /// playing landing, and this one enters, to play once its beats are
    /// registered.
    func show(_ sequence: IntroSequence) {
        guard frozenAt == nil else { return }
        let others = inView.subtracting([sequence])
        inView.subtract(others)
        inView.insert(sequence)
        // Landing what played starts the next one in view: this one.
        for other in others {
            land(other)
        }
        startNext()
    }

    /// A throw came to rest on `sequence` (D11): every throw lands on a tick.
    /// A card that plays its intro ticks its own arrival; one that plays none
    /// (already played, or settled) gets the same tick from here.
    func landed(_ sequence: IntroSequence) {
        guard frozenAt == nil, isActive, !isPlaying(sequence) else { return }
        haptics?.tick(.arrive)
    }

    /// Brings `sequence` into view with its beats, beside any already there.
    func enter(_ sequence: IntroSequence, beats: @escaping () -> [IntroBeat]) {
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

    /// Focus lost or the app inactive: what plays lands, and only its haptics stop; what waits keeps waiting,
    /// unrecorded.
    func settleAll() {
        ending?.cancel()
        ending = nil
        for sequence in ends.keys {
            haptics?.stop(sequence, of: month)
            phases[sequence] = .settled
        }
        ends = [:]
    }

    /// The playing sequence has landed (its time came, or it left the view): the next may start.
    private func land(_ sequence: IntroSequence) {
        guard ends.removeValue(forKey: sequence) != nil else { return }
        ending?.cancel()
        ending = nil
        haptics?.stop(sequence, of: month)
        phases[sequence] = .settled
        startNext()
    }

    /// Starts the first sequence in view, still waiting and registered, in page
    /// order, when the page may play and nothing does. One with nothing to play
    /// lands at once, and the next is tried.
    private func startNext() {
        guard isActive, ends.isEmpty else { return }
        let rank = { (sequence: IntroSequence) in self.order.firstIndex(of: sequence) ?? self.order.count }
        let ready = inView.filter { phase($0) == .waiting && latest[$0] != nil }
        guard let next = ready.min(by: { rank($0) < rank($1) }), let beats = latest[next]?() else { return }
        guard !beats.isEmpty else {
            ledger.record(month, next)
            phases[next] = .settled
            startNext()
            return
        }
        // At its tempo: the keyed beats, the end and the haptics all play the scaled score.
        start(next, beats: beats.map { $0.scaled(by: tempo.factor(next)) })
    }

    private func start(_ sequence: IntroSequence, beats: [IntroBeat]) {
        let begin = now().addingTimeInterval(Self.lead)
        let end = begin.addingTimeInterval(IntroScore.duration(beats))
        keyed[sequence] = Self.index(beats)
        ends[sequence] = end
        phases[sequence] = .playing(begin)
        ledger.record(month, sequence)
        haptics?.play(sequence, of: month, beats: beats, startingAt: begin)
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
