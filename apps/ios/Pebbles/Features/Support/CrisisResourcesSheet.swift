import SwiftUI

/// Crisis lines by country, one tap to call. Presented from Settings and from
/// the quiet success screen. The list is bundled (`crisis-resources.json`).
struct CrisisResourcesSheet: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.locale) private var locale

    private let resources = CrisisResources.bundled
    @State private var code: String

    init() {
        let region = Locale.current.region?.identifier
        _code = State(initialValue: CrisisResources.bundled.defaultCountry(region: region)?.code ?? "")
    }

    private var country: CrisisCountry? {
        resources.countries.first { $0.code == code }
    }

    private var worldwideURL: URL? {
        guard let url = URL(string: resources.worldwide.url), url.scheme == "https" else { return nil }
        return url
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("If you are in danger or thinking about hurting yourself, call now. These lines are free and confidential.")
                        .pebblesListRow(position: .only)
                }

                if !resources.countries.isEmpty {
                    Section {
                        Picker("Country", selection: $code) {
                            ForEach(resources.countries, id: \.code) { item in
                                Text(item.name.text(for: locale)).tag(item.code)
                            }
                        }
                        .pebblesListRow(position: .only)
                    }
                }

                if let country {
                    Section {
                        ForEach(Array(country.lines.enumerated()), id: \.offset) { index, line in
                            lineRow(line)
                                .pebblesListRow(position: pebblesRowPosition(index: index, count: country.lines.count))
                        }
                    }
                }

                if let worldwideURL {
                    Section {
                        Link(destination: worldwideURL) {
                            Label(resources.worldwide.label.text(for: locale), systemImage: "globe")
                        }
                        .pebblesListRow(position: .only)
                    }
                }

                if !resources.verifiedOn.isEmpty {
                    Section {
                        Text("Numbers checked on \(resources.verifiedOn)")
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                            .listRowBackground(Color.clear)
                    }
                }
            }
            .pebblesList()
            .pebblesToolbarTitle("Get support")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    PebbleToolbarButton("Done") { dismiss() }
                }
            }
            .pebblesScreen()
        }
    }

    @ViewBuilder
    private func lineRow(_ line: CrisisLine) -> some View {
        let row = VStack(alignment: .leading, spacing: 2) {
            Text(line.label.text(for: locale))
            Text(line.number).font(.title3)
            if let note = line.note {
                Text(note.text(for: locale)).foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        if let url = URL(string: "tel:\(line.tel)") {
            Link(destination: url) { row }
        } else {
            row
        }
    }
}
