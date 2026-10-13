import SwiftUI

/// The life domain. Single-select; the tap commits and advances (A1).
struct CompleteDomainStep: View {
    let model: CompleteFlowModel

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
