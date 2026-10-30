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
    @Environment(HeldCardHostModel.self) private var heldCard

    @State private var model = AchievementsModel()
    @State private var sheets = AchievementSheetState()
    /// A brief line after a pull that failed, over the page and any sheet.
    @State private var notice: String?

    var body: some View {
        content
            .overlay(alignment: .bottom) { noticeView }
            .animation(.easeOut(duration: 0.2), value: notice)
            .pebblesToolbarTitle("Achievements")
            .task { await load() }
            .pebblesScreen()
    }

    @ViewBuilder
    private var content: some View {
        let locale = SpineStrings.appLocale
        if let page = model.page {
            let lookups = HomeLookups.make(palettes: palettes, refs: refs)
            ScrollView {
                AchievementsPageContent(page: page, lookups: lookups, pulling: model.pulling, onPull: pull,
                                        onOpen: { sheets.sheet = $0 })
                    .padding(.top, Spacing.sm)
            }
            .sheet(item: $sheets.sheet, onDismiss: openPendingMoment) { opened in
                // The model's page, read when drawn: a later read reaches an open sheet.
                AchievementSheetHost(sheet: opened, page: model.page, lookups: lookups, pulling: model.pulling,
                                     notice: notice, dismissNotice: { notice = nil },
                                     seen: { model.opened($0) },
                                     pull: pull, openMoment: { sheets.open($0) })
            }
            // A sheet whose section left the payload is closed by its owner, with what it presents.
            .onChange(of: model.page) { _, now in sheets.close(ifGoneFrom: now) }
            // `onDismiss` may not report when a nested sheet went down with its parent: open the
            // pending moment once the dismissal has had time to finish, if nothing opened it yet.
            .onChange(of: sheets.sheet == nil) { _, closed in
                guard closed, sheets.pendingMoment != nil else { return }
                Task {
                    try? await Task.sleep(for: .milliseconds(450))
                    openPendingMoment()
                }
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

    /// The held card is drawn above the tabs, under any sheet: a row's moment
    /// opens once the sheets are down, and a change on the card reads the page again.
    private func openPendingMoment() {
        guard let id = sheets.takePending() else { return }
        heldCard.open(HeldCardRequest(momentId: id, origin: .you, onChanged: { Task { await load() } }))
    }

    /// A ready mark pulls its ceremony. A row opens the unlock moment with the
    /// karma it paid; a pull the re-read confirms opens it without a karma line
    /// (the moment hides the line at 0). Anything else is logged by the model.
    @ViewBuilder
    private var noticeView: some View {
        if let notice {
            AchievementsNotice(text: notice) { self.notice = nil }
        }
    }

    private func pull(_ stack: AchievementsPage.CeremonyStack) {
        Task {
            let karma: Int?
            let outcome = await model.pull(stack)
            switch outcome {
            case .pulled(let paid): karma = paid
            case .pulledWithoutKarma: karma = nil
            case .alreadyPulled, .notReady, .busy, .failed:
                notice = AchievementsCopy.pullNotice(outcome, locale: SpineStrings.appLocale)
                return
            }
            notify.present(cards: [AchievementsCopy.ceremonyCard(stack, karma: karma, locale: SpineStrings.appLocale)])
        }
    }
}
