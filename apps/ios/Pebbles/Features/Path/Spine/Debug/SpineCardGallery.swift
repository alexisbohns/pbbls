#if DEBUG
import SwiftUI

/// Fixture-week cards by moment number, in the content column of a 393 pt page,
/// each at its primed ratio: the lab's cards and the card references. The
/// photos come from the environment's `SpineSnapSource` (none in the lab, so
/// the cards show their photo bed: decision 7).
struct SpineCardGallery: View {
    let numbers: [Int]
    var width: CGFloat = 393

    // swiftlint:disable:next type_name
    private typealias F = SpineFixtureWeek

    private var moments: [MomentSummary] {
        numbers.compactMap { number in
            F.moments.first { $0.id.uuidString.hasSuffix(String(format: "%012ld", number)) }
        }
    }

    /// The server's persons order for the fixture week (#1226).
    private var order: [String] { F.personsOrder }

    var body: some View {
        VStack(alignment: .leading, spacing: 48) {
            ForEach(moments) { moment in
                SpineCard(
                    moment: moment,
                    emotion: F.palette(for: moment),
                    ratio: moment.firstSnapPath.flatMap { F.photos[$0]?.ratio },
                    column: SpineLayout.column(width: width),
                    soulNames: F.soulNames, soulGlyphs: F.soulGlyphs, soulOrder: order
                )
            }
        }
        .padding(.leading, SpineLayout.colX)
        .padding(.vertical, 48)
        .frame(width: width, alignment: .leading)
        .background(Color.pebbles.surface)
    }
}
#endif
