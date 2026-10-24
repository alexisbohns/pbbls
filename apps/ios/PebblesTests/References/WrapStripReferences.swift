import SwiftUI
import Testing
@testable import Pebbles

extension References {
    /// #1226 W3: the strip as the server sends it (the harness's verbatim
    /// `strip.json`: spacers, three dolmens and the live week), focused on
    /// August, in a plain row since `ImageRenderer` draws a scroll view blank;
    /// and the marks: cairns settled, busy and live, a blank cairn, a dolmen,
    /// each in ink and in grey. Android: the `PathStrip` and marks previews.
    @Suite("Wrap strip references")
    @MainActor
    struct WrapStripReferences {
        @Test(arguments: ReferenceScheme.allCases)
        func strip(scheme: ReferenceScheme) throws {
            let locales: Set<ReferenceLocale> = scheme == .paper ? [.en, .fr] : [.en]
            ReferenceImage.assertMatches(try Self.row(), named: "strip",
                                         config: ReferenceConfig(scheme: scheme, locales: locales, width: nil))
        }

        @Test
        func stripAx2() throws {
            ReferenceImage.assertMatches(try Self.row(), named: "strip",
                                         config: ReferenceConfig(scheme: .paper, dynamicType: .accessibility2,
                                                                 width: nil))
        }

        @Test(arguments: ReferenceScheme.allCases)
        func marks(scheme: ReferenceScheme) {
            let quiet = [DayWeight(moments: 1, topIntensity: 1), DayWeight(moments: 0, topIntensity: 0),
                         DayWeight(moments: 2, topIntensity: 2)]
            let busy = Array(repeating: DayWeight(moments: 3, topIntensity: 3), count: 7)
            let view = VStack(alignment: .leading, spacing: 16) {
                ForEach([Color.pebbles.onSurface, Color.pebbles.outline], id: \.self) { ink in
                    HStack(alignment: .bottom, spacing: 16) {
                        CairnMark(days: quiet, state: .settled, color: ink)
                        CairnMark(days: busy, state: .settled, color: ink)
                        CairnMark(days: quiet, state: .live, color: ink)
                        CairnMark(days: busy, state: .live, color: ink)
                        CairnMark(days: [], state: .live, color: ink)
                        CairnMark(days: [], state: .settled, color: ink)
                        DolmenMark(color: ink)
                    }
                }
            }
            .scaleEffect(2, anchor: .topLeading)
            .frame(width: 2 * 322, height: 2 * 100, alignment: .topLeading)
            .padding(24)
            .background(Color.pebbles.surface)
            ReferenceImage.assertMatches(view, named: "marks", config: ReferenceConfig(scheme: scheme, width: nil))
        }

        private static func row() throws -> some View {
            let calendar = Vectors.paris
            let items = try WrapPayloads.strip().items
            let focus = PathPage.month(CalendarMonth(year: 2026, month: 8))
            return HStack(alignment: .top, spacing: 0) {
                ForEach(items) { item in
                    let page = item.isFocusable ? PathPages.page(of: item, calendar: calendar) : nil
                    StripCell(item: item, isFocused: page == focus, calendar: calendar) {}
                }
            }
            .padding(.horizontal, 16)
            .background(Color.pebbles.surface)
        }
    }
}
