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
        model?.sessionEnded()
        self.userId = userId
        model = userId.map(make)
    }
}

/// What `RootView`'s cover shows: builds the flow from the environment, keyed by the session's user.
/// The id is lowercased here and only here (`OnboardingGate.userKey`): it keys the consent gate's
/// cache and binds the hand-off note, and both are compared to the same form elsewhere.
struct OnboardingFlowHost: View {
    let onLogOut: () -> Void
    let onFinish: (OnboardingHandoffNote?) -> Void

    @Environment(SupabaseService.self) private var supabase
    @State private var slot = OnboardingFlowSlot()

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
