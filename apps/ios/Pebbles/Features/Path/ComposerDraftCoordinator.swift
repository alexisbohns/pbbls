import Foundation
import Observation
import Supabase
import os

/// Owns the composer's draft lifecycle for whichever composer is open — the
/// server draft (a deliberate "save as draft") and the local snapshot (crash
/// insurance). Extracted from `CreatePebbleSheet+Drafts` (D8).
///
/// The two are one payload shape and nothing else; see
/// `docs/superpowers/specs/2026-07-29-drafts-and-autosave-design.md`.
///
/// This type never owns the draft itself — the sheet holds it in `@State`, the
/// flow holds it on `RecordFlowModel`. It returns instructions and the caller
/// applies them, which is what lets one implementation serve both.
@Observable
@MainActor
final class ComposerDraftCoordinator {

    /// What the composer should do when it opens.
    enum Hydration: Equatable {
        /// Resuming a server draft — hydrate from this payload.
        case resume(PebbleDraftPayload)
        /// A local snapshot exists and is worth offering.
        case offerRestore(PebbleDraftPayload)
        /// Nothing to restore.
        case fresh
    }

    /// Whether a resumed draft's glyph is still usable.
    enum GlyphVerdict: Equatable {
        /// Keep it. Carries the glyph row when we could load it for the preview.
        case usable(Glyph?)
        /// Drop it — the user can no longer attach it.
        case unusable
        /// Verification itself failed; leave the draft alone.
        case unknown
    }

    /// The server draft this composer is bound to — the resumed one, or the one
    /// created by the first "Save as draft".
    private(set) var serverDraftId: UUID?
    private(set) var isSavingDraft = false
    private(set) var hasChecked = false

    /// Non-nil while a restore is on offer.
    private(set) var restorableSnapshot: PebbleDraftPayload?
    var isRestorePromptPresented = false

    private let client: SupabaseClient
    private let drafts: PebbleDraftsService
    private let snapshots: ComposerSnapshotStore
    private let autosave: ComposerAutosave
    private let logger = Logger(subsystem: "app.pbbls.ios", category: "composer-drafts")

    init(client: SupabaseClient, drafts: PebbleDraftsService, snapshots: ComposerSnapshotStore) {
        self.client = client
        self.drafts = drafts
        self.snapshots = snapshots
        self.autosave = ComposerAutosave(store: snapshots)
    }

    // MARK: - Hydration

    /// The pure decision. A resumed server draft wins over a local snapshot —
    /// it is the more deliberate of the two, so we never prompt on top of it.
    ///
    /// Returns nil while `refsLoaded` is false: hydrating before reference data
    /// arrives sanitizes the draft against empty sets and silently drops every
    /// soul and collection (#647), the exact failure M47's D7 exists to prevent.
    /// `nonisolated` because it touches no instance state — being callable
    /// without hopping to the main actor is the point of extracting it.
    nonisolated static func hydration(
        resuming: PebbleDraftRecord?,
        refsLoaded: Bool,
        snapshot: PebbleDraftPayload?
    ) -> Hydration? {
        guard refsLoaded else { return nil }
        if let resuming { return .resume(resuming.payload) }
        if let snapshot, !snapshot.isEmpty { return .offerRestore(snapshot) }
        return .fresh
    }

    /// Applies `hydration(…)` once, recording its side effects. Returns nil
    /// while reference data is loading or when it has already run, so callers
    /// can drive it from both `.task` and an `onChange(of: refs.hasLoaded)`.
    func hydrate(resuming: PebbleDraftRecord?, refsLoaded: Bool) -> Hydration? {
        guard !hasChecked else { return nil }
        guard let decision = Self.hydration(
            resuming: resuming, refsLoaded: refsLoaded, snapshot: snapshots.load()
        ) else { return nil }

        hasChecked = true
        switch decision {
        case .resume:
            serverDraftId = resuming?.id
        case .offerRestore(let snapshot):
            restorableSnapshot = snapshot
            isRestorePromptPresented = true
        case .fresh:
            break
        }
        return decision
    }

    /// The user accepted the restore. Hands back the payload and clears the offer.
    func takeRestorableSnapshot() -> PebbleDraftPayload? {
        defer { restorableSnapshot = nil }
        return restorableSnapshot
    }

    /// The user chose to start fresh.
    func discardSnapshot() {
        restorableSnapshot = nil
        autosave.clear()
    }

    // MARK: - Autosave

    func schedule(_ payload: PebbleDraftPayload) { autosave.schedule(payload) }
    func flush() { autosave.flush() }

    // MARK: - Glyph verification

    /// `can_use_glyph` is what `create_pebble` enforces, so passing here means
    /// publish cannot 42501 on the glyph (M47 D7).
    ///
    /// A verification failure is not a reason to lose the user's glyph:
    /// `.unknown` leaves the draft alone, and publishing will surface a clear
    /// message if it really is unusable.
    func verifyGlyph(glyphId: UUID, userId: UUID) async -> GlyphVerdict {
        do {
            let usable: Bool = try await client
                .rpc(
                    "can_use_glyph",
                    params: ["p_glyph_id": glyphId.uuidString, "p_user": userId.uuidString]
                )
                .execute()
                .value
            guard usable else {
                logger.notice("resumed draft referenced an unusable glyph — dropping it")
                return .unusable
            }
            let glyphs: [Glyph] = try await client
                .from("glyphs")
                .select("id, name, strokes, view_box, user_id")
                .eq("id", value: glyphId)
                .limit(1)
                .execute()
                .value
            return .usable(glyphs.first)
        } catch {
            logger.error("glyph verification failed: \(error.localizedDescription, privacy: .private)")
            return .unknown
        }
    }

    // MARK: - Server draft

    /// Intentional "save as draft". Returns nil on success, or a user-facing
    /// message on failure.
    ///
    /// Deliberately does NOT run `snaps.cancelAndCleanup` — that would delete
    /// the snap the draft references.
    func saveAsDraft(payload: PebbleDraftPayload, userId: UUID) async -> String? {
        isSavingDraft = true
        defer { isSavingDraft = false }
        do {
            serverDraftId = try await drafts.save(payload: payload, id: serverDraftId, userId: userId)
            // Once the draft is on the server the local snapshot is redundant.
            autosave.clear()
            await drafts.refreshCount()
            return nil
        } catch {
            logger.error("save draft failed: \(error.localizedDescription, privacy: .private)")
            return "Couldn't save that draft. Please try again."
        }
    }

    /// Publishing consumed the draft. Runs on soft success too: a 5xx carrying
    /// a `pebble_id` still created the pebble, so a kept draft would duplicate it.
    func consumeAfterPublish() async {
        autosave.clear()
        guard let serverDraftId else { return }
        await drafts.deleteIgnoringFailure(id: serverDraftId)
        self.serverDraftId = nil
        await drafts.refreshCount()
    }
}
