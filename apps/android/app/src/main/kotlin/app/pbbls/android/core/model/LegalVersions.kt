package app.pbbls.android.core.model

/**
 * The legal-document versions this build shows and records consent against.
 *
 * Bump these in the SAME change as the `version:` frontmatter of
 * `apps/web/docs/terms/{en,fr}.md` and `apps/web/docs/privacy/{en,fr}.md`;
 * `LegalVersionsTest` fails otherwise.
 * `health_data` and `age_assurance` cite [PRIVACY], as web does
 * (`CONSENT_DOCUMENT_VERSION`): both statements live in the privacy policy.
 */
object LegalVersions {
    const val TERMS = "1.2.0"
    const val PRIVACY = "1.4.0"
}
