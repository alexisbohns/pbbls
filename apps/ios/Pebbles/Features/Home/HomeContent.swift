import SwiftUI

/// The Home page's body, from the greeting to the About list, without the
/// environment: `HomeView` draws it inside its ScrollView with the live
/// service, the Spine Lab and the references draw it with a fixture model.
/// `clock` pins the greeting; nil reads the live minute.
struct HomeContent: View {
    let model: HomeModel
    let lookups: WrapLookups
    let liveMonth: CalendarMonth
    var clock: Date? = nil
    let zone: TimeZone
    let locale: Locale
    var cameraSaving = false
    var cameraError: String? = nil
    var onCamera: () -> Void = {}
    var onQuick: () -> Void = {}
    var onPebble: () -> Void = {}
    var onOpenWrap: (HomeMonth) -> Void = { _ in }
    var onRetryWraps: () -> Void = {}
    var onTask: (HomeChecklist.Task) -> Void = { _ in }
    var onClaim: () -> Void = {}
    var onDismissCameraError: () -> Void = {}

    var body: some View {
        VStack(alignment: .leading, spacing: Spacing.xl) {
            // The greeting re-reads the clock every minute, so the band flips on the hour.
            TimelineView(.periodic(from: .now, by: 60)) { context in
                HomeGreetingView(lines: greetingLines(at: clock ?? context.date))
            }
            .id(TabNavigation.scrollTopAnchor)

            HomeShortcuts(
                onCamera: onCamera,
                onQuick: onQuick,
                onPebble: onPebble,
                cameraDisabled: cameraSaving
            )

            if model.wraps != nil || model.wrapsError != nil {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    HomeWrapsRow(months: model.wraps?.months ?? [], lookups: lookups,
                                 liveMonth: liveMonth, onOpen: onOpenWrap)
                    if let wrapsError = model.wrapsError {
                        HStack(alignment: .firstTextBaseline) {
                            Text(verbatim: wrapsError)
                                .pebblesFont(.callout)
                                .foregroundStyle(Color.pebbles.onSurfaceVariant)
                            Spacer(minLength: 0)
                            Button("home.wraps.retry", action: onRetryWraps)
                                .pebblesFont(.callout)
                        }
                    }
                }
            }

            // HomeChecklistView draws nothing for a done or unavailable checklist.
            if let checklist = model.checklist {
                VStack(alignment: .leading, spacing: Spacing.sm) {
                    HomeChecklistView(checklist: checklist, isClaiming: model.isClaiming,
                                      onTask: onTask, onClaim: onClaim)
                    if let claimError = model.claimError {
                        Text(verbatim: claimError)
                            .pebblesFont(.callout)
                            .foregroundStyle(Color.pebbles.onSurfaceVariant)
                    }
                }
            }

            // Spec §1.6: the About header is static; the rows land with the read.
            GuidesList(guides: model.guides)

            if cameraSaving {
                Text("home.camera.saving")
                    .spineType(.summary)
                    .italic()
                    .foregroundStyle(Color.pebbles.onSurfaceVariant)
            }

            if let cameraError {
                HStack(alignment: .firstTextBaseline) {
                    Text(verbatim: cameraError)
                        .pebblesFont(.callout)
                        .foregroundStyle(Color.pebbles.error)
                    Spacer(minLength: 0)
                    Button(action: onDismissCameraError) {
                        Image(systemName: "xmark").foregroundStyle(Color.pebbles.onSurfaceVariant)
                    }
                    .accessibilityLabel(Text("Close"))
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, Spacing.lg)
        .padding(.top, Spacing.md)
        .padding(.bottom, MonthLayout.pageBottom)
    }

    private func greetingLines(at now: Date) -> HomeGreeting.Lines {
        HomeGreeting.lines(at: now, name: model.name, zone: zone, locale: locale)
    }
}
