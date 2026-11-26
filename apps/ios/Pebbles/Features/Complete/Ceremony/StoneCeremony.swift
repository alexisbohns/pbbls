import Foundation

enum StoneCeremonyPhase: Equatable {
    /// Act 1 is running, from the first frame.
    case setDown(since: Date)
    /// Act 1 rests on its last frame and waits for Continue.
    case paused
    /// Act 2 is running, from Continue.
    case toItsDay(since: Date)
    /// Everything is still.
    case settled
}

/// Where a ceremony that does not run starts.
enum StoneCeremonyFreeze {
    case pause, settled
}

/// The stone reveal ceremony's clock and gates (#1691): which phase it is in, what a tap does,
/// and the frame to draw. Pure and driven by the dates it is handed, so it can be asserted
/// without a view. The view's timer calls `tick` and stops at `endsAt`.
struct StoneCeremony: Equatable {
    let timeline: StoneCeremonyTimeline
    let markedDay: Int
    let heroReach: [Double]
    private(set) var phase: StoneCeremonyPhase

    init(timeline: StoneCeremonyTimeline, markedDay: Int, heroReach: [Double], start: Date) {
        self.timeline = timeline
        self.markedDay = markedDay
        self.heroReach = heroReach
        self.phase = .setDown(since: start)
    }

    init(timeline: StoneCeremonyTimeline, markedDay: Int, heroReach: [Double], frozen: StoneCeremonyFreeze) {
        self.timeline = timeline
        self.markedDay = markedDay
        self.heroReach = heroReach
        self.phase = frozen == .pause ? .paused : .settled
    }

    func frame(at now: Date) -> StoneCeremonyFrame {
        switch phase {
        case .setDown(let since):
            return timeline.act1(at: now.timeIntervalSince(since), heroReach: heroReach)
        case .paused:
            return timeline.act1(at: timeline.pause, heroReach: heroReach)
        case .toItsDay(let since):
            // Act 2 shows a stone that is already carved, whenever Continue was tapped.
            return timeline.act2(at: now.timeIntervalSince(since), markedDay: markedDay)
        case .settled:
            return timeline.settledFrame(markedDay: markedDay)
        }
    }

    var isRunning: Bool {
        switch phase {
        case .setDown, .toItsDay: return true
        case .paused, .settled: return false
        }
    }

    /// Richer input is accepted once the layout and Done are in.
    func acceptsRicherInput(at now: Date) -> Bool {
        switch phase {
        case .setDown, .paused: return false
        case .toItsDay(let since): return now.timeIntervalSince(since) >= timeline.settled
        case .settled: return true
        }
    }

    /// When the running phase ends, so the clock can stop. Nil when the ceremony is still.
    func endsAt() -> Date? {
        switch phase {
        case .setDown(let since): return since.addingTimeInterval(timeline.pause)
        case .toItsDay(let since): return since.addingTimeInterval(timeline.still)
        case .paused, .settled: return nil
        }
    }

    mutating func tick(at now: Date) {
        switch phase {
        case .setDown(let since) where now.timeIntervalSince(since) >= timeline.pause:
            phase = .paused
        case .toItsDay(let since) where now.timeIntervalSince(since) >= timeline.still:
            phase = .settled
        default:
            break
        }
    }

    /// A tap anywhere that is not Continue skips to the next rest.
    mutating func tapOutside(at now: Date) {
        switch phase {
        case .setDown: phase = .paused
        case .toItsDay: phase = .settled
        case .paused, .settled: break
        }
    }

    /// True only when Act 2 starts now. Before the stone has touched down, Continue is not there
    /// to be tapped, so it only skips.
    mutating func tapContinue(at now: Date) -> Bool {
        switch phase {
        case .setDown(let since) where now.timeIntervalSince(since) >= timeline.contact:
            phase = .toItsDay(since: now)
            return true
        case .paused:
            phase = .toItsDay(since: now)
            return true
        default:
            tapOutside(at: now)
            return false
        }
    }
}
