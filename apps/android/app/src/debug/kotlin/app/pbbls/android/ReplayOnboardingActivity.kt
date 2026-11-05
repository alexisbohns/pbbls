package app.pbbls.android

import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import app.pbbls.android.core.data.OnboardingPreferences
import app.pbbls.android.core.data.SupabaseService
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

/**
 * A debug-only launcher entry that puts the signed-in account back at the start of the onboarding
 * (#1510): the two flags the gate reads, then the app. Everything the flow wrote last time stays.
 * Writes the profile directly, which only works while `onboarding_completed` is owner-writable.
 */
@AndroidEntryPoint
class ReplayOnboardingActivity : ComponentActivity() {
    @Inject lateinit var supabase: SupabaseService

    @Inject lateinit var onboarding: OnboardingPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            runCatching {
                supabase.client.auth.awaitInitialization()
                val userId =
                    supabase.client.auth
                        .currentUserOrNull()
                        ?.id ?: error("Sign in first")
                supabase.client
                    .from("profiles")
                    .update(buildJsonObject { put("onboarding_completed", false) }) {
                        filter { eq("user_id", userId) }
                    }
                onboarding.setHasSeenOnboarding(false)
            }.onFailure {
                Log.e(TAG, "replay failed", it)
                Toast.makeText(this@ReplayOnboardingActivity, "Replay failed: ${it.message}", Toast.LENGTH_LONG).show()
            }
            // MainActivity is singleTask: with the app open it would be reused, and its RootViewModel
            // would never re-read the profile. A new process reads everything fresh. The wait lets
            // the settings store finish writing its flag.
            delay(SETTINGS_FLUSH_MS)
            startActivity(
                Intent(this@ReplayOnboardingActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            )
            finish()
            Process.killProcess(Process.myPid())
        }
    }

    private companion object {
        const val TAG = "ReplayOnboarding"
        const val SETTINGS_FLUSH_MS = 500L
    }
}
