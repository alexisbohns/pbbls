import SwiftUI

/// What `RootView` draws above everything in the authed branch, the held card included: a card
/// restored from scene storage must not route around it.
///
/// One opaque ground under both states, so going from the loader to the gate only crossfades the gate
/// and the tabs never show through. It appears at once and fades only on its way out, which is a
/// pass: fading in would show the app beneath, gated, for a quarter of a second.
struct ConsentLayerView: View {
    let layer: OnboardingConsentLayer
    let gate: ConsentGateModel
    let onSignOut: () -> Void

    var body: some View {
        if layer != .open {
            ZStack {
                Color.pebbles.surface.ignoresSafeArea()
                if layer == .gate {
                    ConsentGateView(model: gate, onSignOut: onSignOut)
                } else {
                    // The launch's loader, in the same place: the surface can last a few seconds
                    // (`OnboardingBounds`), and never as an empty screen. The logo hides itself from
                    // VoiceOver, so the label goes on an element of its own.
                    HandcraftedLogoView(shouldSettle: false, startSettled: true)
                        .containerRelativeFrame(.horizontal) { width, _ in width * 0.33 }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(Text("Loading"))
                }
            }
            .contentShape(Rectangle())
            // Nothing beneath is reachable, whichever state it is in.
            .accessibilityElement(children: .contain)
            .accessibilityAddTraits(.isModal)
            .transition(.asymmetric(insertion: .identity, removal: .opacity))
        }
    }
}
