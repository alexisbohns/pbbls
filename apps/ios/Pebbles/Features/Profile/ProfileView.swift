import SwiftUI

struct ProfileView: View {
    @Environment(SupabaseService.self) private var supabase

    var body: some View {
        // Intentional WIP stub; replaced in task 14 of the #451 plan.
        Text(verbatim: "Profile WIP")
            .navigationTitle("Profile")
            .pebblesScreen()
    }
}

#Preview {
    ProfileView()
        .environment(SupabaseService())
}
