import SwiftUI
import UIKit

/// Rounded-rectangle text input with a 1pt border. White fill, muted-foreground
/// for both placeholder and typed content, per the design spec. SwiftUI's
/// default placeholder styling uses `.secondary` and isn't directly recolorable
/// on iOS 17, so we render a custom overlay that disappears once `text` is non-empty.
struct PebblesTextInput: View {
    let placeholder: LocalizedStringResource
    @Binding var text: String
    var isSecure: Bool = false
    var contentType: UITextContentType? = nil
    var keyboard: UIKeyboardType = .default
    var autocapitalization: TextInputAutocapitalization = .sentences
    var autocorrection: Bool = true

    @ScaledMetric(relativeTo: .body) private var minHeight: CGFloat = 52
    @ScaledMetric(relativeTo: .body) private var horizontalPadding: CGFloat = 16
    @ScaledMetric(relativeTo: .body) private var cornerRadius: CGFloat = 12

    var body: some View {
        ZStack(alignment: .leading) {
            if text.isEmpty {
                Text(placeholder)
                    .foregroundStyle(Color.pebblesMutedForeground)
                    .padding(.horizontal, horizontalPadding)
                    .allowsHitTesting(false)
            }

            field
                .font(.body)
                .foregroundStyle(Color.pebblesMutedForeground)
                .tint(Color.pebblesAccent)
                .textContentType(contentType)
                .keyboardType(keyboard)
                .textInputAutocapitalization(autocapitalization)
                .autocorrectionDisabled(!autocorrection)
                .padding(.horizontal, horizontalPadding)
        }
        .frame(minHeight: minHeight)
        .background(
            RoundedRectangle(cornerRadius: cornerRadius).fill(Color.white)
        )
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(Color.pebblesBorder, lineWidth: 1)
        )
    }

    @ViewBuilder
    private var field: some View {
        if isSecure {
            SecureField("", text: $text)
        } else {
            TextField("", text: $text)
        }
    }
}

#Preview("Empty") {
    @Previewable @State var text = ""
    return PebblesTextInput(placeholder: "Email", text: $text)
        .padding()
        .background(Color.pebblesBackground)
}

#Preview("Filled") {
    @Previewable @State var text = "hello@bohns.design"
    return PebblesTextInput(placeholder: "Email", text: $text)
        .padding()
        .background(Color.pebblesBackground)
}

#Preview("Secure") {
    @Previewable @State var text = "hunter22"
    return PebblesTextInput(placeholder: "Password", text: $text, isSecure: true)
        .padding()
        .background(Color.pebblesBackground)
}
