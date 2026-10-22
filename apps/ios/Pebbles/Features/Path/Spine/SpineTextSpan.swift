import SwiftUI

extension Text {
    /// One run of a concatenated `Text` in a Spine rank (#1130). It applies the
    /// same resolution `.spineType(_:)` applies to a view (`SpineType.resolve`),
    /// for the runs of a people line, which must wrap as one text. The caller
    /// passes its environment's Dynamic Type and Bold Text.
    func spineSpan(
        _ style: SpineType.Style, dynamicTypeSize: DynamicTypeSize, legibilityWeight: LegibilityWeight?
    ) -> Text {
        let resolved = SpineType.resolve(style, dynamicTypeSize: dynamicTypeSize, legibilityWeight: legibilityWeight)
        return font(Font(resolved.uiFont)).tracking(resolved.tracking)
    }
}

extension SpineType.Style {
    /// The people line's quiet aside ("with", "& twelve others"): the `people`
    /// rank in its italic, as Android's `aside` span is.
    static var peopleAside: Self {
        var style = Self.people
        style.italic = true
        return style
    }
}
