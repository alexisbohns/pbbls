import SwiftUI

/// You → Achievements (#1306, spec D1): the days kept and five cards, drawn
/// from `get_achievements`. Opening the screen fires `check_achievements()`
/// first: that call is the retroactive grant, so a veteran's history is there
/// on the first visit, and it never celebrates. The device's last page shows at
/// once while the read runs (stale-while-revalidate); a failed read keeps it and
/// logs, and only a first visit with nothing kept shows the error. A ready mark
/// pulls its ceremony through the model, which decides on a fresh read; the
/// ceremony is the existing unlock moment.
struct AchievementsView: View {
    @Environment(SupabaseService.self) private var supabase
    @Environment(AchievementsService.self) private var achievements
    @Environment(AchievementNotificationService.self) private var notify
    @Environment(EmotionPaletteService.self) private var palettes
    @Environment(ReferenceDataService.self) private var refs

    @State private var model = AchievementsModel()

    var body: some View {
        content
            .pebblesToolbarTitle("Achievements")
            .task { await load() }
            .pebblesScreen()
    }

    @ViewBuilder
    private var content: some View {
        let locale = SpineStrings.appLocale
        if let page = model.page {
            ScrollView {
                AchievementsPageContent(page: page, lookups: HomeLookups.make(palettes: palettes, refs: refs),
                                        pulling: model.pulling, onPull: pull)
                    .padding(.top, Spacing.sm)
            }
        } else if model.failed {
            ContentUnavailableView {
                Label {
                    Text(verbatim: AchievementsCopy.errorTitle(locale: locale))
                } icon: {
                    Image(systemName: "exclamationmark.triangle")
                }
            } description: {
                Text(verbatim: AchievementsCopy.errorBody(locale: locale))
            } actions: {
                Button {
                    Task { await load() }
                } label: {
                    Text(verbatim: AchievementsCopy.retry(locale: locale))
                }
            }
        } else {
            ProgressView()
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func load() async {
        if model.service == nil {
            model.service = AchievementsPageService(client: supabase.client, disk: .standard,
                                                    owner: supabase.session?.user.id.uuidString.lowercased())
        }
        await model.load { await achievements.checkIgnoringFailure() }
    }

    /// A ready mark pulls its ceremony. A row opens the unlock moment with the
    /// karma it paid; a pull the re-read confirms opens it without a karma line
    /// (the moment hides the line at 0). Anything else is logged by the model.
    private func pull(_ stack: AchievementsPage.CeremonyStack) {
        Task {
            let karma: Int?
            switch await model.pull(stack) {
            case .pulled(let paid): karma = paid
            case .pulledWithoutKarma: karma = nil
            case .alreadyPulled, .notReady, .busy, .failed: return
            }
            notify.present(cards: [AchievementsCopy.ceremonyCard(stack, karma: karma, locale: SpineStrings.appLocale)])
        }
    }
}
