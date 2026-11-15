import Foundation

/// Which photo a flow is waiting on, from the tap that picked or shot it
/// until it has been handed to the coordinator.
///
/// The crop editor puts a load and a screen between the tap and the
/// coordinator, so for that stretch the coordinator cannot tell the form a
/// photo is coming. This gate does: while it holds a token the flow treats the
/// photo as coming (save and close are blocked), a later pick supersedes an
/// earlier one even when the earlier one's bytes arrive first, and a load that
/// lands after Cancel, Discard or close opens nothing.
struct CropPickGate: Equatable {
    private(set) var token: UUID?
    /// True from `begin` until the bytes are read (the veil / spinner).
    private(set) var isLoading = false

    /// A photo is loading, open in the editor, or being cropped and uploaded.
    var isActive: Bool { token != nil }

    /// Starts a pick or shot; supersedes whichever one came before.
    mutating func begin() -> UUID {
        let new = UUID()
        token = new
        isLoading = true
        return new
    }

    /// The bytes for `token` were read. False when a later pick, or a close,
    /// took over meanwhile: the caller drops what it loaded. When `opened` is
    /// false (nothing usable came back) the gate releases for a current token.
    mutating func finishLoading(_ token: UUID, opened: Bool) -> Bool {
        guard self.token == token else { return false }
        isLoading = false
        if !opened { self.token = nil }
        return true
    }

    /// The pick has reached the coordinator (or failed to). Ignored when a
    /// newer pick holds the gate.
    mutating func release(_ token: UUID) {
        guard self.token == token else { return }
        self.token = nil
        isLoading = false
    }

    /// Cancel in the editor, Discard, close: whatever was coming is dropped.
    mutating func clear() {
        token = nil
        isLoading = false
    }
}
