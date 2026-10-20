package app.pbbls.android.features.path.card

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.pbbls.android.core.model.CardFace
import app.pbbls.android.features.path.card.logic.TurnSettle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The button's turn, end to end (pebble card spec §Behaviour). */
internal const val TURN_MS = 420

/** The reduced-motion crossfade (pebble card spec §Behaviour). */
internal const val TURN_FADE_MS = 150

/** Past this much of a turn the other face is in front: the card is edge-on at 90°. */
internal const val TURN_HALF = 0.5f

/** The face a turn from this one shows. */
internal fun CardFace.other(): CardFace = if (this == CardFace.RECTO) CardFace.VERSO else CardFace.RECTO

/** This face as the footer and its miniature name it: the recto is the moment, the verso the pebble. */
internal fun CardFace.toTarget(): CardFaceTarget = if (this == CardFace.RECTO) CardFaceTarget.MOMENT else CardFaceTarget.PEBBLE

/**
 * The held card's turn (pebble card spec §Behaviour): which [face] is up, and [progress]
 * from it toward the other, 0 to 1. Past [TURN_HALF] the other face is in [front]; a turn
 * that lands on 1 commits, setting [face] to the other and [progress] back to 0 in the
 * same frame, so the picture does not change at the commit (the other face at 180° is the
 * same face at 0°).
 *
 * One turn at a time: the button is ignored while a turn or a drag runs ([turn] says so),
 * and a drag that starts during a turn is not taken. A requested face waits its turn
 * ([awaitFree]) rather than being dropped.
 *
 * [landing] is what is saved: a configuration change mid-turn comes back on the face the
 * turn was heading to, and a drag held past the half on the face in front.
 *
 * @param scope the composition's scope, which the turns run in and cancel with.
 * @param initialProgress a frozen turn, for previews; 0 otherwise.
 */
@Stable
internal class CardTurnState(
    face: CardFace,
    private val scope: CoroutineScope,
    initialProgress: Float = 0f,
) {
    var face by mutableStateOf(face)
        private set

    /** Bounded, so a release's spring ends on a face rather than past it. */
    val progress = Animatable(initialProgress).apply { updateBounds(0f, 1f) }

    /** The face in front: [face] until the half, the other after. Read through this, so a turn changes it twice, not every frame. */
    val front: CardFace by derivedStateOf { if (progress.value > TURN_HALF) this.face.other() else this.face }

    private var running: Job? = null

    /** State, so [awaitFree] can wait for the finger to lift. */
    private var dragging by mutableStateOf(false)

    /** The face a running settle lands on; null while none runs. */
    private var settlingTo: CardFace? by mutableStateOf(null)

    private val isBusy: Boolean get() = dragging || running?.isActive == true

    /**
     * Where the card will rest: the face a running turn lands on, the face in front while
     * a finger holds the card, else [face].
     */
    val landing: CardFace
        get() = settlingTo ?: if (dragging) front else face

    /**
     * Turns to the other face: a rotation, or at once under [reduceMotion], where the caller
     * crossfades [face]. False, and nothing happens, while a turn or a drag runs.
     */
    fun turn(reduceMotion: Boolean): Boolean {
        if (isBusy) return false
        if (reduceMotion) {
            face = face.other()
        } else {
            running = scope.launch(start = CoroutineStart.UNDISPATCHED) { settle(1f, tween(TURN_MS, easing = FastOutSlowInEasing)) }
        }
        return true
    }

    /** Turns to [target] unless the card already rests or lands there; false if a turn or a drag is in the way. */
    fun turnTo(
        target: CardFace,
        reduceMotion: Boolean,
    ): Boolean = target == landing || turn(reduceMotion)

    /** Returns once no drag holds the card and no turn runs: a drag's release may start a settle, so it loops. */
    suspend fun awaitFree() {
        while (true) {
            val job = running
            when {
                dragging -> snapshotFlow { dragging }.first { !it }
                job != null && job.isActive -> job.join()
                else -> return
            }
        }
    }

    /** A drag begins; false while a turn runs, and then the drag is not the card's. */
    fun startDrag(): Boolean {
        if (isBusy) return false
        dragging = true
        return true
    }

    /**
     * The drag has moved the turn to [to]. Snapped at once rather than launched later, so
     * the card keeps up with the finger; under reduced motion nothing turns until the release.
     */
    fun drag(
        to: Float,
        reduceMotion: Boolean,
    ) {
        if (!dragging || reduceMotion) return
        scope.launch(start = CoroutineStart.UNDISPATCHED) { progress.snapTo(to) }
    }

    /**
     * The finger lifts at [at], moving at [velocityPerSecond] turns a second toward the
     * other face: [TurnSettle.target] decides where it settles, a critically damped spring
     * carrying the finger's speed takes it there, and a landing on 1 commits.
     */
    fun release(
        at: Float,
        velocityPerSecond: Float,
        reduceMotion: Boolean,
    ) {
        if (!dragging) return
        dragging = false
        val target = TurnSettle.target(at, velocityPerSecond)
        if (reduceMotion) {
            if (target == 1f) face = face.other()
            return
        }
        // A slow release never starts away from where it settles, or it could cross the half
        // and come back, swapping the face in front twice.
        val velocity = TurnSettle.launchVelocity(target, velocityPerSecond)
        running =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                settle(target, spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow), velocity)
            }
    }

    /** The gesture was taken away (a parent scrolled, the page left): back to the face that was up. */
    fun cancelDrag() {
        if (!dragging) return
        dragging = false
        running = scope.launch(start = CoroutineStart.UNDISPATCHED) { settle(0f, tween(TURN_MS / 2, easing = FastOutSlowInEasing)) }
    }

    private suspend fun settle(
        target: Float,
        spec: AnimationSpec<Float>,
        velocityPerSecond: Float = 0f,
    ) {
        // Launched undispatched, so this is set before the launch returns: [landing] never lags a turn.
        settlingTo = if (target >= 1f) face.other() else face
        try {
            progress.animateTo(target, spec, initialVelocity = velocityPerSecond)
            // No suspension between the two writes, so no frame sees the new face at 180°.
            if (progress.value >= 1f) {
                face = face.other()
                progress.snapTo(0f)
            }
        } finally {
            settlingTo = null
        }
    }

    companion object {
        fun saver(scope: CoroutineScope): Saver<CardTurnState, CardFace> =
            Saver(save = { it.landing }, restore = { CardTurnState(it, scope) })
    }
}
