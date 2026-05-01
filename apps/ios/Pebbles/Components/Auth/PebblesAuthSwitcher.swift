import SwiftUI

/// Login/Sign up segmented switcher. Renders a SwiftUI `Picker(.segmented)`
/// at full content width. Colors are configured globally on
/// `UISegmentedControl.appearance()` at app launch — see `PebblesApp.init`.
struct PebblesAuthSwitcher: View {
    @Binding var mode: AuthView.Mode

    var body: some View {
        Picker("Mode", selection: $mode) {
            ForEach(AuthView.Mode.allCases) { mode in
                Text(mode.label).tag(mode)
            }
        }
        .pickerStyle(.segmented)
    }
}

#Preview {
    @Previewable @State var mode: AuthView.Mode = .login
    return PebblesAuthSwitcher(mode: $mode)
        .padding()
        .background(Color.pebblesBackground)
}
