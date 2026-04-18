import Foundation

/// Source of a single onboarding screen's illustration.
///
/// `.asset` references an image set bundled in `Assets.xcassets`.
/// `.remote` references a CDN/Supabase Storage URL fetched at view time.
/// The split keeps `OnboardingPageView` agnostic to where artwork lives —
/// migrating from local placeholders to remote artwork is a config change.
enum OnboardingImage {
    case asset(String)
    case remote(URL)
}
