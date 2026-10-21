#if DEBUG
import SwiftUI

/// The Spine's debug lab (#1130), Settings → Developer, beside the stone lab:
/// the Spine's pieces on their own, one section each, pressable on a device.
/// Verbatim text throughout, so nothing reaches the string catalogue.
struct SpineLabView: View {
    enum Section: String, CaseIterable, Identifiable {
        case stones, cards, feed, dense
        var id: String { rawValue }
    }

    @Environment(\.dismiss) private var dismiss
    @State private var section: Section = .stones
    @State private var model: PathModel?
    @State private var denseModel: PathModel?
    @AccessibilityFocusState private var slotFocus: UUID?

    var body: some View {
        NavigationStack {
            ScrollView {
                switch section {
                case .stones:
                    SpineStoneGallery(pressable: true)
                case .cards:
                    SpineCardGallery(numbers: [1, 4, 7, 9, 13, 15])
                case .feed:
                    lazyDays(model)
                case .dense:
                    lazyDays(denseModel)
                }
            }
            .background(Color.pebbles.surface)
            .task {
                model = await SpineFixtureWeek.model()
                let dense = PathModel(calendar: SpineFixtureWeek.calendar, now: { SpineFixtureWeek.today })
                await dense.load { SpineFixtureWeek.dense }
                dense.updateSouls(names: SpineFixtureWeek.soulNames, glyphs: SpineFixtureWeek.soulGlyphs)
                denseModel = dense
            }
            .toolbar {
                ToolbarItem(placement: .principal) {
                    Picker(selection: $section) {
                        ForEach(Section.allCases) { Text(verbatim: $0.rawValue).tag($0) }
                    } label: {
                        Text(verbatim: "Section")
                    }
                    .pickerStyle(.segmented)
                }
                ToolbarItem(placement: .topBarTrailing) {
                    Button { dismiss() } label: { Text(verbatim: "Done") }
                }
            }
        }
    }

    /// The days in a `LazyVStack`, as the page will lay them out: the stone budget's test bed.
    @ViewBuilder
    private func lazyDays(_ model: PathModel?) -> some View {
        if let model, let page = model.page(for: SpineFixtureWeek.weekStart) {
            let context = SpineFixtureWeek.context(model, page: page)
            LazyVStack(spacing: 0) {
                ForEach(SpineWeekRows.days(page)) { row in
                    SpineRowView(row: row, context: context, slotFocus: $slotFocus)
                }
            }
            .background(alignment: .top) { SpineGround() }
            .padding(.top, SpineLayout.feedTop - SpineLayout.seamOverhang)
        }
    }
}
#endif
