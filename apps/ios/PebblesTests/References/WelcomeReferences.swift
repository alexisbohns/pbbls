import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// A pure SwiftUI view in B1's variable Ysabeau: it exercises the face,
    /// the locale pass and Dynamic Type.
    @Suite("Welcome references")
    @MainActor
    struct WelcomeReferences {

        @Test(arguments: ReferenceScheme.allCases)
        func slide(scheme: ReferenceScheme) {
            ReferenceImage.assertMatches(SlideFixture(), named: "slide",
                                         config: ReferenceConfig(scheme: scheme, locales: [.en, .fr]))
        }

        @Test(.enabled(if: ReferenceLocale.current == .fr))
        func slideAx2() {
            let config = ReferenceConfig(scheme: .paper, locales: [.fr], dynamicType: .accessibility2)
            ReferenceImage.assertMatches(SlideFixture(), named: "slide", config: config)
        }
    }
}

private struct SlideFixture: View {
    var body: some View {
        WelcomeSlideView(step: WelcomeSteps.all[1])
            .padding(.vertical, 24)
            .background(Color.system.background)
    }
}
