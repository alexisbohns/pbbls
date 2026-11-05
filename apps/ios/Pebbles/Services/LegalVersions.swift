import Foundation

/// The legal-document versions this build shows and records consent against.
///
/// Bump these in the SAME change as the `version:` frontmatter of
/// `apps/web/docs/terms/{en,fr}.md` and `apps/web/docs/privacy/{en,fr}.md`;
/// `LegalVersionsTests` fails otherwise. `health_data` and `age_assurance`
/// cite `privacy`, as web and Android do: both statements live in the privacy
/// policy. Mirrors Android's `core/model/LegalVersions.kt`.
enum LegalVersions {
    static let terms = "1.2.0"
    static let privacy = "1.4.0"
}
