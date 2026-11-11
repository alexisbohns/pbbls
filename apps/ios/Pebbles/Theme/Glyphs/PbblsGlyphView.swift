import SwiftUI

/// A `PbblsGlyph` in one of its forms (spec §2), drawn as a template image on
/// a square side that follows Dynamic Type through `@ScaledMetric` (24 pt at
/// the default size, relative to `.body` unless told otherwise). It takes the
/// ambient foreground style unless `style` is given, and is hidden from
/// accessibility unless `label` is. A form change animates when the caller
/// animates it: a crossfade with a slight scale, none under Reduce Motion
/// (spec D9). Being an `Image`, it also draws in tab bars and swipe actions.
struct PbblsGlyphView: View {
    private let glyph: PbblsGlyph
    private let form: GlyphForm
    private let style: AnyShapeStyle?
    private let label: LocalizedStringResource?
    @ScaledMetric private var side: CGFloat
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    init(_ glyph: PbblsGlyph, size: CGFloat = 24, form: GlyphForm = .regular,
         relativeTo textStyle: Font.TextStyle = .body,
         style: AnyShapeStyle? = nil, label: LocalizedStringResource? = nil) {
        self.glyph = glyph
        self.form = form
        self.style = style
        self.label = label
        _side = ScaledMetric(wrappedValue: size, relativeTo: textStyle)
    }

    /// `PbblsGlyphView(.home, .tile)`: at a named size.
    init(_ glyph: PbblsGlyph, _ size: PbblsGlyphSize, form: GlyphForm = .regular,
         relativeTo textStyle: Font.TextStyle = .body,
         style: AnyShapeStyle? = nil, label: LocalizedStringResource? = nil) {
        self.init(glyph, size: size.rawValue, form: form, relativeTo: textStyle, style: style, label: label)
    }

    var body: some View {
        let image = Image(uiImage: GlyphCatalog.shared.image(glyph, form: form, side: side))
            .renderingMode(.template)
            .resizable()
            .scaledToFit()
            .frame(width: side, height: side)
            .id(form)
            .transition(reduceMotion ? .identity : .opacity.combined(with: .scale(scale: 0.9)))
        labelled(styled(image))
    }

    @ViewBuilder
    private func styled<Content: View>(_ content: Content) -> some View {
        if let style {
            content.foregroundStyle(style)
        } else {
            content
        }
    }

    @ViewBuilder
    private func labelled<Content: View>(_ content: Content) -> some View {
        if let label {
            content.accessibilityLabel(Text(label))
        } else {
            content.accessibilityHidden(true)
        }
    }
}
