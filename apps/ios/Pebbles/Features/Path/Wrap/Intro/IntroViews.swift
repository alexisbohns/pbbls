import SwiftUI

private struct IntroClockKey: EnvironmentKey {
    static let defaultValue: IntroClock? = nil
}

private struct IntroSequenceKey: EnvironmentKey {
    static let defaultValue: IntroSequence? = nil
}

private struct IntroNowKey: EnvironmentKey {
    static let defaultValue = Date.distantPast
}

extension EnvironmentValues {
    /// The month page's intro; nil, the default, draws every figure settled.
    var introClock: IntroClock? {
        get { self[IntroClockKey.self] }
        set { self[IntroClockKey.self] = newValue }
    }

    /// The sequence a view belongs to, set by its host.
    var introSequence: IntroSequence? {
        get { self[IntroSequenceKey.self] }
        set { self[IntroSequenceKey.self] = newValue }
    }

    /// The playing sequence's frame date, set by its host's timeline.
    var introNow: Date {
        get { self[IntroNowKey.self] }
        set { self[IntroNowKey.self] = newValue }
    }
}

/// Hands its content the reveal of the sequence around it at the frame's
/// date: settled with no clock, outside a sequence, under Reduce Motion, or
/// while VoiceOver runs.
struct IntroReader<Content: View>: View {
    private let content: (IntroReveal) -> Content

    @Environment(\.introClock) private var clock
    @Environment(\.introSequence) private var sequence
    @Environment(\.introNow) private var now
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver

    init(@ViewBuilder content: @escaping (IntroReveal) -> Content) {
        self.content = content
    }

    var body: some View {
        content(reveal)
    }

    private var reveal: IntroReveal {
        guard let clock, let sequence, IntroPolicy.plays(reduceMotion: reduceMotion, voiceOver: voiceOver) else {
            return .settled
        }
        return clock.reveal(sequence, at: clock.frozenAt ?? now)
    }
}

/// How a SwiftUI part lands (#1241 §Choreography).
enum IntroLanding {
    /// Fades in, rising 6 pt.
    case rise
    /// Fades in where it is.
    case fade
    /// Grows from 0.4, overshooting once.
    case pop
}

extension View {
    /// Lands this view on `key`'s beat. A nil key, or no intro around it, leaves it as it is.
    func introReveal(_ key: IntroKey?, _ landing: IntroLanding = .rise) -> some View {
        modifier(IntroRevealModifier(key: key, landing: landing))
    }

    /// Makes this view a sequence of the intro: it registers its beats as it
    /// appears, leaves as it disappears, and redraws on each frame while it
    /// plays. `beats` is asked for only as the sequence starts.
    func introSequence(_ sequence: IntroSequence, beats: @escaping () -> [IntroBeat]) -> some View {
        modifier(IntroSequenceHost(sequence: sequence, beats: beats))
    }
}

private struct IntroRevealModifier: ViewModifier {
    let key: IntroKey?
    let landing: IntroLanding

    func body(content: Content) -> some View {
        if let key {
            IntroReader { reveal in
                IntroLanded(content: content, progress: reveal(key), landing: landing)
            }
        } else {
            content
        }
    }
}

/// A part at its progress: landed (1), it draws exactly as it would without the intro.
private struct IntroLanded<Content: View>: View {
    let content: Content
    let progress: Double
    let landing: IntroLanding

    var body: some View {
        switch landing {
        case .rise:
            let eased = IntroMotion.easeOut(progress)
            content
                .opacity(eased)
                .offset(y: IntroMotion.riseOffset * CGFloat(1 - eased))
        case .fade:
            content.opacity(IntroMotion.easeOut(progress))
        case .pop:
            content
                .opacity(IntroMotion.popOpacity(progress))
                .scaleEffect(CGFloat(IntroMotion.popScale(progress)))
        }
    }
}

private struct IntroSequenceHost: ViewModifier {
    let sequence: IntroSequence
    let beats: () -> [IntroBeat]

    @Environment(\.introClock) private var clock

    func body(content: Content) -> some View {
        if let clock {
            Group {
                if clock.frozenAt != nil {
                    content
                } else {
                    // Only the playing sequence redraws per frame, and only while it plays.
                    TimelineView(.animation(paused: !clock.isPlaying(sequence))) { timeline in
                        content.environment(\.introNow, timeline.date)
                    }
                }
            }
            .environment(\.introSequence, sequence)
            // The deck says when it is on top (`IntroClock.show`); it plays then, with these beats.
            .onAppear { clock.register(sequence, beats: beats) }
            // Unloaded by the lazy stack, or gone with its page.
            .onDisappear { clock.leave(sequence) }
        } else {
            content
        }
    }
}
