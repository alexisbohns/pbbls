import Foundation
import Supabase

/// The consent-ledger seam `ConsentGateModel` is tested against.
protocol ConsentServicing: Sendable {
    /// The signed-in user's active rows (neither withdrawn nor superseded).
    func active() async throws -> [ActiveConsent]
    /// One consent act through `record_consent`, idempotent server-side.
    func record(kind: ConsentKind, documentVersion: String, source: String) async throws
}

/// The `user_consents` ledger. Reads are a single-table select that RLS scopes
/// to the owner; writes go through the `record_consent` definer RPC, because
/// the table has no client write policy at all. Errors propagate: the caller
/// owns the gate's state. Mirrors Android's `core/data/ConsentService.kt`.
struct ConsentService: ConsentServicing {
    let client: SupabaseClient

    func active() async throws -> [ActiveConsent] {
        try await client
            .from("user_consents")
            .select("kind, document_version")
            .is("withdrawn_at", value: nil)
            .is("superseded_at", value: nil)
            // One attempt: the consent gate holds the app on this read, and the SDK's retries (three
            // more, 60 s each) would outlast the gate's ceiling (`ConsentGateModel.ledgerRead`).
            .retry(enabled: false)
            .execute()
            .value
    }

    func record(kind: ConsentKind, documentVersion: String, source: String) async throws {
        try await client
            .rpc("record_consent", params: RecordConsentParams(
                pKind: kind.rawValue,
                pDocumentVersion: documentVersion,
                pSource: source
            ))
            .execute()
    }
}

/// `record_consent(p_kind, p_document_version, p_source)`.
private struct RecordConsentParams: Encodable {
    let pKind: String
    let pDocumentVersion: String
    let pSource: String

    enum CodingKeys: String, CodingKey {
        case pKind = "p_kind"
        case pDocumentVersion = "p_document_version"
        case pSource = "p_source"
    }
}
