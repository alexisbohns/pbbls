extension PbblsGlyph {
    /// The forms this glyph has, in the generator's order (spec D3).
    var forms: [GlyphForm] {
        GlyphCatalog.shared.glyph(self).formKeys.compactMap(GlyphForm.init(key:))
    }

    /// The form a state shows on this glyph (spec D6): the state's first form
    /// the glyph has. `k` is the caller's progress for `.progress`.
    func form(for state: PbblsGlyphState, k: Int = 0) -> GlyphForm {
        GlyphRenderer.resolve(states: GlyphCatalog.shared.states, formKeys: GlyphCatalog.shared.glyph(self).formKeys,
                              state: state.rawValue, k: k)
    }
}
