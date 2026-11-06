import SwiftUI

/// The seven-step flow's shell (#1510): a real `NavigationStack` whose toolbar carries Back, the
/// progress stones and Skip or Later, over a surface the flow paints itself. Mirrors Android's
/// `OnboardingScreen`. Nothing presents it yet; the root gate does in Part 5.
struct OnboardingFlowView: View {
    let model: OnboardingFlowModel
    let onLogOut: () -> Void
    let onFinish: () -> Void

    @State private var isMovingBack = false
    /// The carve cover is driven here, not by `model.carvingFor` alone: a cover presented while the
    /// glyph sheet is still dismissing is dropped, so a person's carve waits for the sheet's `onDismiss`.
    @State private var showingCarve = false

    private struct SheetPlace: Identifiable { let id: Int }

    var body: some View {
        NavigationStack {
            stepBody
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color.pebbles.surface.ignoresSafeArea())
                .navigationBarTitleDisplayMode(.inline)
                .toolbar { toolbar }
        }
        .pebblesScreen()
        .animation(.snappy(duration: 0.28), value: model.step)
        .onChange(of: model.step) { old, new in
            let steps = OnboardingFlowSteps.all
            isMovingBack = (steps.firstIndex(of: new) ?? 0) < (steps.firstIndex(of: old) ?? 0)
        }
        .onChange(of: model.carvingFor) { _, target in
            switch target {
            case .you: showingCarve = true
            case .none: showingCarve = false
            case .person: break
            }
        }
        .sheet(
            item: Binding(
                get: { model.glyphSheetFor.map(SheetPlace.init) },
                set: { if $0 == nil { model.closeGlyphSheet() } }
            ),
            onDismiss: { if model.carvingFor != nil { showingCarve = true } },
            content: glyphSheet
        )
        .fullScreenCover(isPresented: $showingCarve, onDismiss: {
            if model.carvingFor != nil { model.carveCancelled() }
        }, content: {
            GlyphCarveSheet(onSaved: model.carved)
        })
        .onChange(of: model.isDone) { _, done in
            if done { onFinish() }
        }
        .task { await model.load() }
    }

    @ToolbarContentBuilder
    private var toolbar: some ToolbarContent {
        if model.stepIndex > 0 {
            ToolbarItem(placement: .topBarLeading) {
                PebbleToolbarButton(action: model.back) { Image(systemName: "chevron.left") }
                    .accessibilityLabel(Text("Back"))
                    .disabled(model.chromeDisabled)
            }
        }
        ToolbarItem(placement: .principal) {
            ProgressStones(current: model.stepIndex, count: OnboardingFlowSteps.all.count)
        }
        if model.step.skip != .none {
            ToolbarItem(placement: .topBarTrailing) {
                PebbleToolbarButton(action: model.skip) {
                    Text(model.step.skip == .later ? "Later" : "Skip")
                }
                .disabled(model.chromeDisabled)
            }
        }
    }

    private var stepBody: some View {
        stepContent
            .id(model.step.id)
            .transition(.asymmetric(
                insertion: .move(edge: isMovingBack ? .leading : .trailing).combined(with: .opacity),
                removal: .move(edge: isMovingBack ? .trailing : .leading).combined(with: .opacity)
            ))
    }

    @ViewBuilder
    private var stepContent: some View {
        switch model.step.id {
        case .promise:
            PromiseStep(letter: model.letter, unavailable: model.letterUnavailable, onContinue: model.next)
        case .reasons:
            ReasonsStep(selected: model.reasons, onToggle: model.toggleReason, onContinue: model.next)
        case .consent:
            ConsentStep(
                state: model.consent,
                onToggle: model.toggleConsent,
                onContinue: model.next,
                onLogOut: onLogOut
            )
        case .you:
            YouStep(
                name: model.name,
                glyphs: model.glyphChoices,
                selectedId: model.glyphId,
                canContinue: model.canContinueYou,
                onNameChange: model.setName,
                onSelect: { model.pickGlyph($0.id) },
                onDraw: model.drawForYou,
                onContinue: model.next
            )
        case .people:
            PeopleStep(
                people: model.people,
                glyphFor: glyph(forPerson:),
                canAdd: model.people.count < PeopleStep.places && !model.people.contains(where: \.isEditing),
                canContinue: model.canContinuePeople,
                cta: model.peopleCTA,
                onAdd: model.addPerson,
                onNameChange: model.setPersonName,
                onCommit: model.commitPerson,
                onEdit: model.editPerson,
                onChangeGlyph: model.openGlyphSheet,
                onContinue: model.next
            )
        case .pace:
            PaceStep(
                selected: model.pace,
                isCompleting: model.isCompleting,
                failed: model.completeFailed,
                onSelect: model.selectPace,
                onFinish: model.next
            )
        // The hand-off is the `complete_onboarding` call and Home: it never shows.
        case .handoff:
            EmptyView()
        }
    }

    private func glyph(forPerson index: Int) -> Glyph? {
        guard model.people.indices.contains(index) else { return nil }
        let id = model.people[index].glyphId ?? model.defaultGlyphId(index)
        return model.glyphChoices.first { $0.id == id }
    }

    @ViewBuilder
    private func glyphSheet(_ place: SheetPlace) -> some View {
        if model.people.indices.contains(place.id) {
            let person = model.people[place.id]
            SoulGlyphSheet(
                personName: person.name,
                glyphs: model.glyphChoices,
                currentGlyphId: person.glyphId ?? model.defaultGlyphId(place.id),
                onUse: { model.pickPersonGlyph(place.id, $0) },
                onDraw: { model.drawForPerson(place.id) }
            )
        }
    }
}
