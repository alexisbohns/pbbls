import Foundation
import Supabase
import os

/// The publish half of the composer: invoke `compose-pebble`, recover the
/// soft-success case, and map failures to user-facing copy.
///
/// Extracted from `CreatePebbleSheet` (D8) so the sheet and the record flow
/// share one implementation. Every branch here is a bug that was found and
/// fixed once already; a second hand-rolled copy is how they come back.
struct PebblePublisher {
    let client: SupabaseClient

    private static let logger = Logger(subsystem: "app.pbbls.ios", category: "pebble-publisher")

    /// Publishes `draft`, returning the compose response.
    ///
    /// Folds in the soft-success case: `compose-pebble` can return 5xx after
    /// the pebble row was already inserted, and the body then carries
    /// `pebble_id`. That pebble exists, so treating it as a failure would
    /// strand the user's work and — worse — leave the draft in place to be
    /// published a second time.
    func publish(
        draft: PebbleDraft,
        formSnap: FormSnap?,
        userId: UUID
    ) async throws -> ComposePebbleResponse {
        let payload = PebbleCreatePayload(from: draft, formSnap: formSnap, userId: userId)
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601

        do {
            return try await client.functions.invoke(
                "compose-pebble",
                options: FunctionInvokeOptions(body: ComposePebbleRequest(payload: payload)),
                decoder: decoder
            )
        } catch let functionsError as FunctionsError {
            guard let pebbleId = Self.softSuccessPebbleId(from: functionsError) else {
                Self.logger.error(
                    "compose-pebble failed: \(functionsError.localizedDescription, privacy: .private)"
                )
                throw functionsError
            }
            Self.logger.warning("compose-pebble returned 5xx but pebble_id found — treating as published")
            // No render and no karma delta: the compose step is what failed.
            // Callers degrade to a text-only presentation, and `notifyEarned`
            // no-ops on a zero amount.
            return ComposePebbleResponse(
                pebbleId: pebbleId, renderSvg: nil, renderVersion: nil, karmaDelta: nil
            )
        }
    }

    /// Tries to extract a `pebble_id` from a `FunctionsError.httpError` body.
    /// Returns nil when the body is absent, unparseable, or has no such key.
    ///
    /// Internal rather than private so the recovery rule is directly testable —
    /// it is the branch that decides whether a user's pebble is lost.
    static func softSuccessPebbleId(from error: FunctionsError) -> UUID? {
        guard case let .httpError(_, data) = error, !data.isEmpty else { return nil }
        return try? JSONDecoder().decode(PebbleIdPartial.self, from: data).pebbleId
    }
}

private struct ComposePebbleRequest: Encodable {
    let payload: PebbleCreatePayload
}

private struct PebbleIdPartial: Decodable {
    let pebbleId: UUID
    enum CodingKeys: String, CodingKey {
        case pebbleId = "pebble_id"
    }
}

/// Maps a thrown error to user-facing copy. Module-level so `CreatePebbleSheet`,
/// `EditPebbleSheet` and `RecordFlowView` share one mapping.
func userMessageForPebbleSaveError(_ error: Error) -> String {
    if let fnError = error as? FunctionsError, case let .httpError(_, data) = fnError,
       let body = try? JSONDecoder().decode([String: String].self, from: data) {
        let message = body["error"] ?? body["message"] ?? ""
        if message.contains("media_quota_exceeded") || message.contains("P0001") {
            return "Photo limit reached on this pebble."
        }
    }
    if let pipelineError = error as? ImagePipelineError {
        switch pipelineError {
        case .unsupportedFormat:    return "That image format isn't supported."
        case .decodeFailed:         return "Couldn't read the image."
        case .encodeFailed:         return "Couldn't process the image."
        case .tooLargeAfterResize:  return "That image is too large to attach."
        }
    }
    return "Couldn't save your pebble. Please try again."
}
