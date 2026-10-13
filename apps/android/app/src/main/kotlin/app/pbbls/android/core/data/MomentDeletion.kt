package app.pbbls.android.core.data

import app.pbbls.android.core.model.MomentDeleteChoice

/** `delete_pebble` takes the PEBBLE id and keeps the moment; `delete_moment` takes the moment id. */
suspend fun MomentWriteServicing.delete(
    momentId: String,
    pebbleId: String?,
    choice: MomentDeleteChoice,
) = when (choice) {
    MomentDeleteChoice.MOMENT -> deleteMoment(momentId)
    MomentDeleteChoice.PEBBLE_ONLY -> deletePebble(requireNotNull(pebbleId) { "no pebble to delete on $momentId" })
}
