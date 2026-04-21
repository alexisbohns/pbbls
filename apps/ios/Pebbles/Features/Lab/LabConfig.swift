import Foundation

/// Static configuration for the Lab tab. Values that aren't content (and
/// therefore don't belong in the `logs` table) live here.
enum LabConfig {
    /// The WhatsApp community invite link, surfaced on the featured card.
    /// Replace with your group's real chat.whatsapp.com URL.
    static let whatsappInviteURL = URL(string: "https://chat.whatsapp.com/REPLACE_ME")!

    /// Supabase storage bucket where announcement cover images live.
    static let assetsBucket = "lab-assets"
}
