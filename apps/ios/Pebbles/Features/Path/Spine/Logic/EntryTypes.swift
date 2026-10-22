import CoreGraphics

/// A no-photo entry's name (#1130 R6): its cut, its line height (× size) and the
/// stone's anchor, the centre of the name's first line in points from the top.
/// `SpineType.Cut` is the Spine's type cut (size, weight, italic, tracking);
/// this type only adds the two numbers around it.
struct EntryType: Equatable {
    let name: SpineType.Cut
    let lineHeight: CGFloat
    let anchor: CGFloat
}

/// The type ranks of a pebble's name, a port of `apps/web/lib/utils/spine/entry-type.ts`
/// (phone sizes). Intensity sets the size and valence the cut, so neither rests on
/// colour alone (A7).
enum EntryTypes {
    private static let entrySize: [Int: CGFloat] = [1: 19, 2: 24, 3: 31]
    private static let entryWeight: [Int: Int] = [1: 600, 0: 460, -1: 340]
    private static let contentsWeight: [Int: Int] = [1: 400, 2: 470, 3: 620]
    private static let cardTitleWeight: [Int: Int] = [1: 600, 0: 480, -1: 360]

    static func entryType(intensity: Int, valence: Int) -> EntryType {
        let size = entrySize[intensity] ?? 24
        let lowlight = valence == -1
        let cut = SpineType.Cut(size: size, weight: entryWeight[valence] ?? 460, italic: lowlight,
                                trackingEm: lowlight ? -0.004 : -0.012)
        return EntryType(name: cut, lineHeight: 1.12, anchor: size * 0.6)
    }

    /// A contents row's name: weight by intensity, lowlights in a light italic.
    static func contentsNameType(intensity: Int, valence: Int) -> SpineType.Cut {
        let lowlight = valence == -1
        return SpineType.Cut(size: 16.5, weight: lowlight ? 350 : (contentsWeight[intensity] ?? 470),
                             italic: lowlight, trackingEm: 0)
    }

    /// A card's title cut. Its size is in card units (4.4u); `SpineType.Style.cardTitle(u:cut:)` scales it.
    static func cardTitleCut(valence: Int) -> SpineType.Cut {
        SpineType.Cut(size: 4.4, weight: cardTitleWeight[valence] ?? 480, italic: valence == -1, trackingEm: 0)
    }
}
