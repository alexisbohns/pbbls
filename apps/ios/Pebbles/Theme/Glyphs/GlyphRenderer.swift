import UIKit

/// `renderForm` and `resolveState` from `packages/icons/scripts/lib`, in Swift.
/// The forms contract (`GlyphFormsContractTests`) holds them equal.
enum GlyphRenderer {
    private static func stroked(_ d: String) -> RenderedPathData {
        RenderedPathData(d: d, fill: false, evenOdd: false, stroke: true)
    }
    private static func filled(_ d: String) -> RenderedPathData {
        RenderedPathData(d: d, fill: true, evenOdd: false, stroke: true)
    }
    private static func carved(_ d: String) -> RenderedPathData {
        RenderedPathData(d: d, fill: true, evenOdd: true, stroke: false)
    }
    /// A filled mark or step: its carved fill when lines run inside it.
    private static func lit(_ p: GlyphPathData) -> RenderedPathData { p.fill.map(carved) ?? filled(p.d) }

    /// The paths a form draws, overrides first (spec D3, D4). `progress(0)` is
    /// regular; a form the glyph does not have draws regular (the generator
    /// throws there, a view should not).
    static func render(_ glyph: GlyphData, _ form: GlyphForm) -> [RenderedPathData] {
        if case .progress(0) = form.fill { return render(glyph, GlyphForm(.regular, marked: form.marked)) }
        let key = form.key
        guard glyph.formKeys.contains(key) else { return key == "regular" ? [] : render(glyph, .regular) }
        if let override = glyph.overrides[key] { return override }
        let path = { (i: Int) in glyph.paths.indices.contains(i) ? glyph.paths[i] : nil }
        switch form.fill {
        case .dotted:
            return glyph.dotted ?? []
        case .solid, .parts:
            guard let fill = glyph.carved[key] else { return render(glyph, .regular) }
            return [carved(fill.d)]
                + fill.keep.compactMap { path($0).map { stroked($0.d) } }
                + fill.lit.compactMap { path($0).map(lit) }
        case .regular, .progress:
            break
        }
        var k = 0
        if case .progress(let n) = form.fill { k = n }
        // A hidden path is drawn only when its role fills it; everything else is stroked.
        // A filled path with a carved `fill` draws that instead, and the lines it is carved through are left out.
        let fills = { (p: GlyphPathData) in
            (form.marked && p.role == .mark) || (p.role == .step && (p.step ?? 0) <= k)
        }
        let cut = Set(glyph.paths.filter { fills($0) && $0.fill != nil }.flatMap { $0.cuts ?? [] })
        return glyph.paths.enumerated().compactMap { i, p in
            let on = fills(p)
            if (p.hidden == true && !on) || cut.contains(i) { return nil }
            return on ? lit(p) : stroked(p.d)
        }
    }

    /// The form a state shows on a glyph with these form keys (`resolveState` in `states.ts`).
    static func resolve(states: [String: [String]], formKeys: [String], state: String, k: Int = 0) -> GlyphForm {
        let n = formKeys.filter { $0.hasPrefix("progress-") && !$0.hasSuffix("-marked") }.count
        for word in states[state] ?? ["regular"] {
            switch word {
            case "regular": return .regular
            case "marked" where formKeys.contains("marked"): return .marked
            case "solid" where formKeys.contains("solid"): return .solid
            case "progress-n" where n > 0: return GlyphForm(.progress(n))
            case "progress-k" where n > 0: return GlyphForm(.progress(max(0, min(k, n))))
            default: continue
            }
        }
        return .regular
    }
}

extension GlyphRenderer {
    /// Draws paths on the 24-unit square, scaled to `side` points at 3×, as a
    /// template image: fill then stroke per path, in order, as SVG paints them,
    /// with the 1.75 stroke, round caps and joins.
    static func draw(_ paths: [RenderedPathData], side: CGFloat) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { context in
            let cg = context.cgContext
            cg.scaleBy(x: side / 24, y: side / 24)
            cg.setFillColor(UIColor.black.cgColor)
            cg.setStrokeColor(UIColor.black.cgColor)
            cg.setLineWidth(1.75)
            cg.setLineCap(.round)
            cg.setLineJoin(.round)
            for path in paths {
                let shape = GlyphPathParser.cgPath(path.d)
                if path.fill {
                    cg.addPath(shape)
                    cg.fillPath(using: path.evenOdd ? .evenOdd : .winding)
                }
                if path.stroke {
                    cg.addPath(shape)
                    cg.strokePath()
                }
            }
        }
        return image.withRenderingMode(.alwaysTemplate)
    }
}
