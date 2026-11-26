import CoreGraphics
import Foundation
import os
import SwiftUI

/// One engine shape seed's veins and fossil in the seed's own canvas units
/// (`LitStoneGeometry.seedCanvas`): the iOS port of the web's `STONE_CARVINGS`
/// entry and Android's `StoneCarving` (#1690). The engine outline is left out:
/// the lit stone's edge is the wobbled backdrop silhouette (spec D3).
struct LitStoneCarving {
    /// Each vein in document order, with its `stroke-width` in seed units.
    let veins: [(path: Path, width: Double)]
    /// The filled carving, with its `fill-rule`; nil when the seed has none
    /// (large highlight).
    let fossil: (path: Path, evenOdd: Bool)?
}

/// The nine seeds' carvings, read through `StoneCarvingArt.parsedSeed` (the
/// bundled `stone-shape-<size>-<polarity>.svg`, split by `StoneShapeParser`)
/// and parsed once into a fixed array indexed by size × polarity, in
/// `allCases` order. Nothing is looked up through a dictionary at draw time.
enum LitStoneCarvings {

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "stone")

    static func carving(_ size: ValenceSizeGroup, _ polarity: ValencePolarity) -> LitStoneCarving {
        table[index(size, polarity)]
    }

    private static func index(_ size: ValenceSizeGroup, _ polarity: ValencePolarity) -> Int {
        let row: Int
        switch size {
        case .small:  row = 0
        case .medium: row = 1
        case .large:  row = 2
        }
        let column: Int
        switch polarity {
        case .lowlight:  column = 0
        case .neutral:   column = 1
        case .highlight: column = 2
        }
        return row * 3 + column
    }

    /// Built once, in a fixed order: `ValenceSizeGroup.allCases` (small,
    /// medium, large) × `ValencePolarity.allCases` (lowlight, neutral,
    /// highlight), the order `index` counts in.
    private static let table: [LitStoneCarving] = {
        var carvings: [LitStoneCarving] = []
        for size in ValenceSizeGroup.allCases {
            for polarity in ValencePolarity.allCases {
                carvings.append(read(size, polarity))
            }
        }
        return carvings
    }()

    /// A seed that is missing or unparseable (already logged by the loader)
    /// draws without carvings rather than crashing.
    private static func read(_ size: ValenceSizeGroup, _ polarity: ValencePolarity) -> LitStoneCarving {
        guard let parsed = StoneCarvingArt.parsedSeed(size: size, polarity: polarity) else {
            return LitStoneCarving(veins: [], fossil: nil)
        }
        var veins: [(path: Path, width: Double)] = []
        for (position, stroke) in parsed.veins.enumerated() {
            guard let path = SVGPathParser.parse(stroke.d) else {
                logger.error("stone: vein \(position, privacy: .public) of \(size.rawValue, privacy: .public)-\(polarity.rawValue, privacy: .public) does not parse")
                continue
            }
            veins.append((path: Path(path), width: stroke.width))
        }
        var fossil: (path: Path, evenOdd: Bool)?
        if let fill = parsed.fossil {
            if let path = SVGPathParser.parse(fill.d) {
                fossil = (path: Path(path), evenOdd: fill.usesEvenOddFill)
            } else {
                logger.error("stone: fossil of \(size.rawValue, privacy: .public)-\(polarity.rawValue, privacy: .public) does not parse")
            }
        }
        return LitStoneCarving(veins: veins, fossil: fossil)
    }
}
