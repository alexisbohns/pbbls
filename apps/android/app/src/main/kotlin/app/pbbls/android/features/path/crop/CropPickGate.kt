package app.pbbls.android.features.path.crop

/** What a flow does with a loaded photo that may be waiting for a free screen (iOS `CropPresentation`). */
enum class CropPresentation {
    /** Nothing is waiting. */
    NONE,

    /** Waiting for something else on screen (the close dialog) to go. */
    WAIT,

    /** Nothing else is on screen and the pick is current: open the editor. */
    PRESENT,

    /** The pick was superseded or closed meanwhile: drop it. */
    DROP,
}

/**
 * Which photo a flow is waiting on, from the tap that picked or shot it until it has been handed to
 * the coordinator (#1659, iOS `CropPickGate`).
 *
 * The crop editor puts a load and a screen between the tap and the coordinator, so for that stretch
 * the coordinator cannot tell the form a photo is coming. This gate does: while it holds a token the
 * flow treats the photo as coming (save and close are blocked), a later pick supersedes an earlier one
 * even when the earlier one's bytes arrive first, and a load that lands after Cancel, Discard or close
 * opens nothing. Not thread-safe: the ViewModel touches it on the main thread only.
 */
class CropPickGate {
    private var lastToken = 0L

    var token: Long? = null
        private set

    /** True from [begin] until the bytes are read (the tile's spinner). */
    var isLoading: Boolean = false
        private set

    /** A photo is loading, open in the editor, or being cropped and handed over. */
    val isActive: Boolean get() = token != null

    /** Starts a pick or shot; supersedes whichever one came before. */
    fun begin(): Long {
        lastToken += 1
        token = lastToken
        isLoading = true
        return lastToken
    }

    /**
     * The bytes for [token] were read. False when a later pick, or a close, took over meanwhile: the
     * caller drops what it loaded. When [opened] is false (nothing usable came back) the gate releases
     * for a current token.
     */
    fun finishLoading(
        token: Long,
        opened: Boolean,
    ): Boolean {
        if (this.token != token) return false
        isLoading = false
        if (!opened) this.token = null
        return true
    }

    /** Whether a loaded photo waiting under [pendingToken] may open the editor now. [canPresent] is the flow's "nothing else is on screen". */
    fun presentation(
        pendingToken: Long?,
        canPresent: Boolean,
    ): CropPresentation =
        when {
            pendingToken == null -> CropPresentation.NONE
            token != pendingToken -> CropPresentation.DROP
            canPresent -> CropPresentation.PRESENT
            else -> CropPresentation.WAIT
        }

    /** The pick has reached the coordinator (or failed to). Ignored when a newer pick holds the gate. */
    fun release(token: Long) {
        if (this.token != token) return
        this.token = null
        isLoading = false
    }

    /** Cancel in the editor, Discard, close: whatever was coming is dropped. */
    fun clear() {
        token = null
        isLoading = false
    }
}
