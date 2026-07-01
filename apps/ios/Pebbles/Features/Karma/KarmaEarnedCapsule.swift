import SwiftUI
import UIKit

/// In-app "+N karma" pastille. A translucent glass capsule that pops up from
/// the bottom-center of the screen. Works on every device (no Dynamic Island
/// needed) and on any iOS 17+ version. Same visual language as the karma stat
/// in `PathBottomBar` (sparkle + accent). Tap to dismiss; VoiceOver announces.
struct KarmaEarnedCapsule: View {
    let content: KarmaEarnedContent
    let onTap: () -> Void

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: "sparkle")
                .foregroundStyle(Color.accent.primary)
            Text("+\(content.amount) karma")
                .font(.ysabeauSemibold(16))
                .foregroundStyle(Color.system.foreground)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 11)
        // Glassy pastille: translucent material + a bright hairline rim and a
        // soft shadow to lift it off the content behind.
        .background(.regularMaterial, in: Capsule())
        .overlay(Capsule().strokeBorder(.white.opacity(0.22), lineWidth: 0.5))
        .shadow(color: .black.opacity(0.18), radius: 16, y: 6)
        .contentShape(Capsule())
        .onTapGesture(perform: onTap)
        .accessibilityElement()
        .accessibilityLabel("Earned \(content.amount) karma, \(String(localized: content.reason.label))")
        .accessibilityAddTraits(.isStaticText)
    }
}

/// SwiftUI root hosted inside the overlay window: renders the active pastille
/// pinned to the bottom-center, animating in/out. `Color.clear` fills the space
/// so the window has a hit-testable (but pass-through) root.
struct KarmaOverlayRoot: View {
    @Environment(KarmaNotificationService.self) private var karma

    var body: some View {
        ZStack(alignment: .bottom) {
            Color.clear
            if let earned = karma.activeCapsule {
                KarmaEarnedCapsule(content: earned) { karma.dismissCapsule() }
                    .padding(.bottom, 44)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
        .animation(.spring(response: 0.42, dampingFraction: 0.72), value: karma.activeCapsule)
    }
}

/// A window whose empty areas pass touches through to the app below; only the
/// pastille itself is interactive. Lets the karma flash float above presented
/// sheets (create/edit/detail) without blocking interaction with them.
final class KarmaPassthroughWindow: UIWindow {
    override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
        guard let hit = super.hitTest(point, with: event) else { return nil }
        // Touches on the hosting controller's root view (the transparent
        // background) pass through; touches on the pastille's subviews don't.
        return hit == rootViewController?.view ? nil : hit
    }
}

// MARK: - Previews

#Preview("Pastille · over content") {
    // A busy gradient stands in for real app content so the translucent glass
    // and the shadow read the way they will in-app (a plain background hides
    // both). Toggle light/dark with the appearance control in the canvas.
    ZStack {
        LinearGradient(
            colors: [.indigo, .purple, .orange],
            startPoint: .topLeading, endPoint: .bottomTrailing
        )
        .ignoresSafeArea()

        VStack {
            Spacer()
            KarmaEarnedCapsule(
                content: KarmaEarnedContent(amount: 5, reason: .pebbleCreated),
                onTap: {}
            )
            .padding(.bottom, 44)
        }
    }
}

#Preview("Pastille · isolated") {
    KarmaEarnedCapsule(
        content: KarmaEarnedContent(amount: 12, reason: .pebbleEnriched),
        onTap: {}
    )
    .padding(40)
}

/// Owns the overlay window for the karma pastille. Created once from the active
/// window scene and bound to the shared `KarmaNotificationService`.
@MainActor
final class KarmaOverlayWindowController {
    private var window: KarmaPassthroughWindow?

    func attachIfNeeded(service: KarmaNotificationService) {
        guard window == nil else { return }
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        guard let scene = scenes.first(where: { $0.activationState == .foregroundActive })
            ?? scenes.first else { return }

        let window = KarmaPassthroughWindow(windowScene: scene)
        window.windowLevel = .alert + 1  // above presented sheets
        window.backgroundColor = .clear
        let host = UIHostingController(rootView: KarmaOverlayRoot().environment(service))
        host.view.backgroundColor = .clear
        window.rootViewController = host
        window.isHidden = false
        self.window = window
    }
}
