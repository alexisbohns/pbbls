import SwiftUI

/// A month's page (#1226, D6): the cover, then the sections the server listed,
/// in its order. Each *more* opens the month's sheet on its list; the day view
/// (circle or calendar) is one setting for every month. When a milestone's
/// held card closes, VoiceOver comes back to its row (the focused page only,
/// so a realised neighbour never takes it). With an intro context, the page
/// plays its intro (#1241) while on screen, focused and the app active,
/// skipped by a tap; under Reduce Motion or VoiceOver it drops the intro for
/// good. Android: `MonthPage`.
struct MonthPage: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let isFocused: Bool
    /// The model's mirror of the held card's closing; cleared once used.
    @Binding var returnFocus: UUID?
    let onWeek: (CalendarDay) -> Void
    let onOpen: (MomentSummary) -> Void

    @AppStorage(DayView.storageKey) private var dayView: DayView = .circle
    /// The list the sheet opened on; the page's own state, never the model's.
    @State private var sheet: MonthSection?
    @AccessibilityFocusState private var milestoneFocus: UUID?
    /// The page's intro; nil without a context, or once `IntroPolicy` refused it, and the page draws settled.
    @State private var intro: IntroClock?
    /// On screen: a tab switch or a full-screen cover hides the page without a tap on it.
    @State private var isVisible = false
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.accessibilityVoiceOverEnabled) private var voiceOver
    @Environment(\.scenePhase) private var scenePhase

    init(wrap: MonthWrap, lookups: WrapLookups, isFocused: Bool, returnFocus: Binding<UUID?>,
         onWeek: @escaping (CalendarDay) -> Void, onOpen: @escaping (MomentSummary) -> Void,
         intro context: IntroContext? = nil) {
        self.wrap = wrap
        self.lookups = lookups
        self.isFocused = isFocused
        _returnFocus = returnFocus
        self.onWeek = onWeek
        self.onOpen = onOpen
        // Built with the page, not on appear: a first frame without it would draw settled, then blank.
        _intro = State(initialValue: context.map {
            IntroClock(month: wrap.month, order: IntroClock.order(wrap), ledger: $0.ledger, haptics: $0.haptics)
        })
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                MonthPageSections(wrap: wrap, lookups: lookups, dayView: dayView, focus: $milestoneFocus,
                                  onWeek: onWeek, onOpen: onOpen, onMore: { sheet = $0 })
            }
            .padding(.bottom, MonthLayout.pageBottom)
        }
        .coordinateSpace(name: IntroSpace.name)
        .onAppear { isVisible = true }
        .onDisappear { isVisible = false }
        .environment(\.introClock, intro)
        // A tap anywhere skips the intro, and still does its job.
        .simultaneousGesture(TapGesture().onEnded { intro?.skip() })
        .onChange(of: playsIntro, initial: true) { _, plays in intro?.isActive = plays }
        // Refused, the intro is gone for the page's life: turning the setting off again never blanks and replays it.
        .onChange(of: allowsIntro, initial: true) { _, allowed in
            guard !allowed else { return }
            intro?.settleAll()
            intro = nil
        }
        .onChange(of: returnFocus) { _, id in
            guard isFocused, let id else { return }
            milestoneFocus = id
            returnFocus = nil
        }
        .sheet(item: $sheet) { tab in
            MonthSheet(wrap: wrap, lookups: lookups, initial: tab)
        }
    }

    /// The page may play: on screen, focused, the app active, the intro allowed.
    private var playsIntro: Bool {
        isVisible && isFocused && scenePhase == .active && allowsIntro
    }

    /// Neither Reduce Motion nor VoiceOver on.
    private var allowsIntro: Bool {
        IntroPolicy.plays(reduceMotion: reduceMotion, voiceOver: voiceOver)
    }
}

/// The page's rows in the server's order; its own view, so the references lay
/// it out without the page's scroll view.
struct MonthPageSections: View {
    let wrap: MonthWrap
    let lookups: WrapLookups
    let dayView: DayView
    var focus: AccessibilityFocusState<UUID?>.Binding?
    let onWeek: (CalendarDay) -> Void
    let onOpen: (MomentSummary) -> Void
    let onMore: (MonthSection) -> Void

    var body: some View {
        MonthCoverView(wrap: wrap, lookups: lookups)
        ForEach(wrap.sections, id: \.self) { section in
            switch section {
            case .weeks:
                WeeksSummary(wrap: wrap, lookups: lookups, onWeek: onWeek)
            case .milestones:
                MilestonesSection(wrap: wrap, lookups: lookups, onOpen: onOpen, focus: focus)
            case .persons:
                PersonsSection(wrap: wrap, lookups: lookups, dayView: dayView) { onMore(.persons) }
            case .domains:
                DomainsSection(wrap: wrap, lookups: lookups) { onMore(.domains) }
            case .gem:
                EmotionGem(wrap: wrap, lookups: lookups) { onMore(.gem) }
            case .tone, .hours:
                // One head for both figures, over whichever comes first.
                if section == wrap.sections.first(where: { $0 == .tone || $0 == .hours }) {
                    MonthSectionHead(label: WrapCopy.insights(locale: SpineStrings.appLocale),
                                     hint: WrapCopy.insightsHint(locale: SpineStrings.appLocale))
                }
                if section == .tone {
                    ToneFigure(wrap: wrap)
                } else {
                    HoursFigure(wrap: wrap, lookups: lookups)
                }
            }
        }
    }
}
