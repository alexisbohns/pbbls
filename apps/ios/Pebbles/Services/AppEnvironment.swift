import Foundation

/// Typed access to build-time configuration values injected via
/// `Config/Secrets.xcconfig` → `Info.plist`. Fails loud and early if
/// a value is missing so setup bugs don't become runtime mysteries.
enum AppEnvironment {
    static let supabaseURL: URL = {
        guard let raw = Bundle.main.object(forInfoDictionaryKey: "SupabaseURL") as? String,
              !raw.isEmpty,
              let url = URL(string: raw) else {
            fatalError(
                "SupabaseURL missing or invalid in Info.plist. " +
                "Did you copy Config/Secrets.example.xcconfig → Config/Secrets.xcconfig?"
            )
        }
        return url
    }()

    static let supabaseAnonKey: String = {
        guard let key = Bundle.main.object(forInfoDictionaryKey: "SupabaseAnonKey") as? String,
              !key.isEmpty else {
            fatalError(
                "SupabaseAnonKey missing in Info.plist. " +
                "Did you copy Config/Secrets.example.xcconfig → Config/Secrets.xcconfig?"
            )
        }
        return key
    }()
}
