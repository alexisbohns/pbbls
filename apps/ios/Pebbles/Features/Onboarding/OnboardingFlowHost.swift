import Observation
import SwiftUI

/// One flow per signed-in user (#1510 D10): a second account never sees the first's answers, and the
/// first's model is told its session is over, which is what stops the consent acts it has left from
/// going out under whoever signs in next. Signed out, there is no flow at all.
@Observable
@MainActor
final class OnboardingFlowSlot {
    private(set) var model: OnboardingFlowModel?
    @ObservationIgnored private var userId: String?

    func sync(userId: String?, make: (String) -> OnboardingFlowModel) {
        guard userId != self.userId else { return }
        end()
        self.userId = userId
        model = userId.map(make)
    }

    /// No flow until the next `sync`, whoever that is for.
    func end() {
        model?.sessionEnded()
        userId = nil
        model = nil
    }
}

/// What `RootView`'s cover shows: builds the flow from the environment, keyed by the session's user.
/// The id takes the gates' one form (`OnboardingGate.userKey`, as in `RootView` and `HomeView`): it
/// keys the consent gate's cache and binds the hand-off note, and both are compared by string.
struct OnboardingFlowHost: View {
    let slot: OnboardingFlowSlot
    let onLogOut: () -> Void
    /// The cover made it: a presentation can be dropped without a word (`OnboardingPresentation`).
    let onCoverUp: () -> Void
    let onFinish: (OnboardingHandoffNote?) -> Void

    @Environment(SupabaseService.self) private var supabase

    private var userId: String? { OnboardingGate.userKey(supabase.session?.user.id) }

    var body: some View {
        Group {
            if let model = slot.model {
                OnboardingFlowView(model: model, onLogOut: onLogOut, onFinish: { onFinish(model.doneNote) })
                    // A new model is a new screen: step transitions and the carve cover start over.
                    .id(ObjectIdentifier(model))
            } else {
                // The first frame, and the last ones of a sign-out, while the cover goes.
                Color.pebbles.surface.ignoresSafeArea()
            }
        }
        // The flow ends by finishing or logging out: VoiceOver's two-finger escape must not close it.
        .accessibilityElement(children: .contain)
        .accessibilityAction(.escape) {}
        .onAppear(perform: onCoverUp)
        .onChange(of: userId, initial: true) { _, id in
            slot.sync(userId: id) { user in
                OnboardingFlowModel(
                    service: OnboardingService(client: supabase.client),
                    consents: ConsentService(client: supabase.client),
                    userId: user
                )
            }
        }
    }
}
