import SwiftUI

/// Step 5 — the life domain, with its glyph and description (D6).
struct RecordDomainStep: View {
    let model: RecordFlowModel

    @Environment(ReferenceDataService.self) private var refs

    var body: some View {
        DomainPickerContent(
            domains: refs.domains,
            selected: model.draft.domainId
        ) { picked in
            model.select(domainId: picked)
        }
    }
}
