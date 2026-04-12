import SwiftUI

struct ProfileView: View {
    @Environment(SupabaseService.self) private var supabase

    var body: some View {
        NavigationStack {
            VStack(spacing: 24) {
                Text("Profile")
                    .foregroundStyle(.secondary)

                Spacer()

                Button(role: .destructive) {
                    Task {
                        await supabase.signOut()
                    }
                } label: {
                    Text("Log out")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .tint(.red)
                .padding(.horizontal, 24)
                .padding(.bottom, 24)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .navigationTitle("Profile")
        }
    }
}

#Preview {
    ProfileView()
        .environment(SupabaseService())
}
