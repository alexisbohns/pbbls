import SwiftUI

struct PathView: View {
    var body: some View {
        NavigationStack {
            Text("Path")
                .foregroundStyle(.secondary)
                .navigationTitle("Path")
        }
    }
}

#Preview {
    PathView()
}
