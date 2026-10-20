/// The OpenType features a Ysabeau rank may ask for. The tag strings are the
/// ones Android's `SpineTypography` passes, so both surfaces shape alike.
enum YsabeauFeature: Hashable, CaseIterable {
    /// `SC`: small caps from lower case and from capitals.
    case smallCaps
    /// `LIN`: lining, proportional figures.
    case lining
    /// `TAB`: lining, tabular figures.
    case tabular

    var tags: [String] {
        switch self {
        case .smallCaps: return ["smcp", "c2sc"]
        case .lining:    return ["lnum", "pnum"]
        case .tabular:   return ["lnum", "tnum"]
        }
    }
}
