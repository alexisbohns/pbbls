package app.pbbls.android.features.path.wrap.intro

/** Which backend the intro's haptics play through (D18), from what the vibrator can do. */
enum class IntroHapticTier {
    COMPOSITION,
    WAVEFORM,
    VIEW,
    NONE,
    ;

    companion object {
        fun of(
            hasVibrator: Boolean,
            tick: Boolean,
            amplitude: Boolean,
        ): IntroHapticTier =
            when {
                !hasVibrator -> NONE
                tick -> COMPOSITION
                amplitude -> WAVEFORM
                else -> VIEW
            }
    }
}
