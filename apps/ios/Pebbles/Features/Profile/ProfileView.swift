import SwiftUI

struct ProfileView: View {
    var body: some View {
        NavigationStack {
            Text("Profile")
                .foregroundStyle(.secondary)
                .navigationTitle("Profile")
        }
    }
}

#Preview {
    ProfileView()
}
