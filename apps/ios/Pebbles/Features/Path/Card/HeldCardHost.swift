import SwiftUI

/// The held card's layer (#1160 D1), in `RootView`'s `ZStack` above
/// `PebblesTabView`: not a presentation, so it flies on every supported iOS with
/// no guard, and the Spine stays mounted beneath with its slot to return to.
/// It draws the ground (`Color.pebbles.surface`, fading with the chrome), the
/// page, and the leading 20 pt edge strip whose swipe scrubs the close.
///
/// It animates the host's flight state: the open from the slot's measured rect
/// once the page reports its rest (a 0.45 s spring, the chrome in over its last
/// 60 %), or a 0.2 s fade; the close back to the slot's rect read again when
/// the committed face is the recto, else a 0.2 s fade. The slot hides for the
/// flight and shows again on its last frame.
///
/// VoiceOver: a modal layer (the tabs beneath are hidden by `RootView`), the
/// escape gesture closes it. New pattern, named in the E4 PR.
struct HeldCardHost: View {
    @Environment(HeldCardHostModel.self) private var host
    @Environment(CardFrameRegistry.self) private var registry
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.layoutDirection) private var layoutDirection

    private var rightToLeft: Bool { layoutDirection == .rightToLeft }

    var body: some View {
        if let request = host.request {
            GeometryReader { proxy in
                ZStack(alignment: .topLeading) {
                    Color.pebbles.surface
                        .opacity(host.chrome)
                        .ignoresSafeArea()
                    HeldCardPage(request: request, onRest: { rested($0, id: request.momentId) }, onClose: close)
                        .offset(x: rightToLeft ? -host.pageShift : host.pageShift)
                        .opacity(host.pageOpacity)
                    edgeStrip(width: proxy.size.width)
                }
            }
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
            .accessibilityAction(.escape) { close() }
            .task(id: request.momentId) {
                // A fading open does not wait for a rest: the page may be a spinner.
                guard host.phase == .opening, !host.flies else { return }
                withAnimation(.easeOut(duration: 0.2), completionCriteria: .logicallyComplete) {
                    host.pageOpacity = 1
                    host.chrome = 1
                } completion: {
                    host.opened()
                }
            }
        }
    }

    // MARK: - Open

    private func rested(_ rect: CGRect, id: UUID) {
        guard host.reportRest(rect), host.flies else { return }
        // The slot hides on the frame the card appears on it: never both, never neither.
        registry.hidden = id
        withAnimation(.spring(duration: 0.45, bounce: 0), completionCriteria: .logicallyComplete) {
            host.flight = 1
        } completion: {
            host.opened()
        }
        withAnimation(.easeOut(duration: 0.27).delay(0.18)) { host.chrome = 1 }
    }

    // MARK: - Close

    /// A second Back while a close runs is ignored: `finishClose()` fires once.
    private func close() {
        guard let id = host.request?.momentId, host.phase != .idle else { return }
        if case .closing = host.phase { return }
        beginClose(id: id)
        completeClose(width: nil)
    }

    /// The slot hides whenever the close flies, including after an open that faded.
    private func beginClose(id: UUID) {
        if host.beginClose(sourceRect: registry.frames[id], reduceMotion: reduceMotion) {
            registry.hidden = id
        }
    }

    private func completeClose(width: CGFloat?) {
        if host.flies {
            withAnimation(.spring(duration: 0.45, bounce: 0), completionCriteria: .logicallyComplete) {
                host.flight = 0
                host.chrome = 0
            } completion: {
                finish()
            }
        } else {
            withAnimation(.easeOut(duration: 0.2), completionCriteria: .logicallyComplete) {
                host.pageOpacity = 0
                host.chrome = 0
                if let width { host.pageShift = width }
            } completion: {
                finish()
            }
        }
    }

    private func finish() {
        registry.hidden = nil
        host.finishClose()
    }

    // MARK: - The edge swipe

    /// The leading 20 pt (trailing in RTL, by layout direction) has priority
    /// over the card's turn; the card starts 16 pt in, and they move in opposite
    /// directions. It starts under the top bar, so Back keeps its 44 pt target.
    /// Release with the turn's rule: a flick of 1.5 screens a second carries,
    /// else past the half completes; a cancel springs back in 0.21 s.
    private func edgeStrip(width: CGFloat) -> some View {
        HStack(spacing: 0) {
            Color.clear
                .frame(width: 20)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0, coordinateSpace: .global)
                        .onChanged { value in
                            guard let id = host.request?.momentId, host.phase != .opening, host.phase != .idle else {
                                return
                            }
                            if case .closing = host.phase {} else { beginClose(id: id) }
                            let dx = rightToLeft ? -value.translation.width : value.translation.width
                            host.scrub(Double(max(0, dx) / max(width, 1)), width: width)
                        }
                        .onEnded { value in
                            guard case .closing(let progress) = host.phase else { return }
                            let vx = rightToLeft ? -value.velocity.width : value.velocity.width
                            let velocity = Double(vx / max(width, 1))
                            if TurnSettle.target(progress: progress, velocity: velocity) >= 1 {
                                completeClose(width: width)
                            } else {
                                withAnimation(.spring(duration: 0.21, bounce: 0)) { host.cancelClose() }
                            }
                        }
                )
            Spacer(minLength: 0)
        }
        .padding(.top, CardLayout.turnTarget)
        .accessibilityHidden(true)
    }
}
