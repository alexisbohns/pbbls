import SwiftUI

/// Where VoiceOver lands when the held card opens (#1146 §Flight): the
/// recto's title, or the verso's "Your pebble".
enum CardFocus: Hashable {
    case title, versoHeading
}

extension View {
    /// `accessibilityFocused(_:equals:)` when the card passes a binding; nothing on the Spine.
    @ViewBuilder
    func cardFocus(_ binding: AccessibilityFocusState<CardFocus?>.Binding?, _ value: CardFocus) -> some View {
        if let binding {
            accessibilityFocused(binding, equals: value)
        } else {
            self
        }
    }
}
