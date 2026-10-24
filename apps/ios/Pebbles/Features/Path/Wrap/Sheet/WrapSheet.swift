import SwiftUI

/// One sheet over several lists (#1226, D4; maintainer decision A1, 2026-10-24):
/// the iOS form of Android's `TabbedSheetPager`, after Weather's conditions
/// sheet. The title is a pull-down menu naming the open list and offering the
/// others; a horizontal swipe turns to the next list; Done closes. It opens at
/// half the screen and drags to the top, and its fixed detents never resize it
/// as the reader moves between a short list and a long one. No page dots: the
/// title says which list is open. Each page scrolls itself. The open list
/// belongs to the caller's view state, never to a model.
struct WrapSheet<Tab: Hashable, Page: View>: View {
    let tabs: [Tab]
    let initial: Tab
    let title: (Tab) -> String
    let page: (Tab) -> Page

    @Environment(\.dismiss) private var dismiss
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// Seeded with the tapped list, so the title names it from the first frame.
    @State private var position: Tab?

    init(tabs: [Tab], initial: Tab, title: @escaping (Tab) -> String, @ViewBuilder page: @escaping (Tab) -> Page) {
        self.tabs = tabs
        self.initial = initial
        self.title = title
        self.page = page
        _position = State(initialValue: initial)
    }

    private var current: Tab { position ?? initial }

    var body: some View {
        NavigationStack {
            ScrollViewReader { proxy in
                ScrollView(.horizontal) {
                    LazyHStack(spacing: 0) {
                        ForEach(tabs, id: \.self) { tab in
                            ScrollView {
                                page(tab)
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    .padding(.bottom, 24)
                            }
                            .containerRelativeFrame(.horizontal)
                            .id(tab)
                        }
                    }
                    .scrollTargetLayout()
                }
                .scrollTargetBehavior(.paging)
                .scrollIndicators(.hidden)
                .scrollPosition(id: $position)
                // iOS 17 can drop the binding's first write: the reader puts the pager on the tapped list.
                .onAppear { proxy.scrollTo(initial) }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) { switcher }
                ToolbarItem(placement: .confirmationAction) {
                    PebbleToolbarButton("Done") { dismiss() }
                }
            }
            .toolbarBackground(Color.pebbles.surface, for: .navigationBar)
            // The lists scroll in nested scroll views the bar cannot see: keep its paper on.
            .toolbarBackground(.visible, for: .navigationBar)
            .background(Color.pebbles.surface)
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .presentationBackground(Color.pebbles.surface)
    }

    /// The open list's name, and every list behind it.
    private var switcher: some View {
        Menu {
            Picker(selection: Binding(get: { current }, set: { turn(to: $0) })) {
                ForEach(tabs, id: \.self) { tab in
                    Text(verbatim: title(tab)).tag(tab)
                }
            } label: {
                EmptyView()
            }
        } label: {
            HStack(spacing: 4) {
                Text(verbatim: title(current))
                    .spineType(.personName)
                Image(systemName: "chevron.down")
                    .imageScale(.small)
                    .spineType(.personName)
                    .accessibilityHidden(true)
            }
            .foregroundStyle(Color.pebbles.onSurface)
        }
    }

    private func turn(to tab: Tab) {
        if reduceMotion { position = tab } else { withAnimation { position = tab } }
    }
}
