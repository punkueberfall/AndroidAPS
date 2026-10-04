package app.aaps.activities

import android.content.Intent
import android.os.Build
import android.os.Bundle
import app.aaps.MainActivity
import app.aaps.plugins.configuration.activities.DaggerAppCompatActivityWithResult

/**
 * Entry point for external apps that want to open the Bolus Wizard
 * with pre-filled carbs, or the Carbs dialog with pre-filled extended
 * carbs (eCarbs). Forwards to MainActivity which then shows the
 * matching dialog. The user must confirm through the standard AAPS
 * confirmation flow.
 *
 * Intent contract:
 *   action: info.nightscout.androidaps.action.OPEN_BOLUS_WIZARD
 *   extras:
 *     carbs  (int,    required, 1..150, grams)
 *     notes  (string, optional)
 *     source (string, optional, free-form caller tag)
 *
 *   action: info.nightscout.androidaps.action.OPEN_CARBS_DIALOG
 *   extras:
 *     carbs    (int,    required, 1..150, grams)
 *     duration (int,    optional, 0..10, hours; > 0 makes them eCarbs)
 *     notes    (string, optional)
 *     source   (string, optional, free-form caller tag)
 */
class WizardLaunchActivity : DaggerAppCompatActivityWithResult() {

    companion object {
        const val ACTION_OPEN_BOLUS_WIZARD = "info.nightscout.androidaps.action.OPEN_BOLUS_WIZARD"
        const val ACTION_OPEN_CARBS_DIALOG = "info.nightscout.androidaps.action.OPEN_CARBS_DIALOG"
        const val EXTRA_CARBS    = "carbs"
        const val EXTRA_DURATION = "duration"
        const val EXTRA_NOTES    = "notes"
        const val EXTRA_SOURCE   = "source"
        const val INTERNAL_CARBS = "open_wizard_carbs"
        const val INTERNAL_NOTES = "open_wizard_notes"
        const val INTERNAL_ECARBS          = "open_carbs_dialog_carbs"
        const val INTERNAL_ECARBS_DURATION = "open_carbs_dialog_duration"
        const val INTERNAL_ECARBS_NOTES    = "open_carbs_dialog_notes"

        private const val MAX_CARBS = 150
        // Same bound as AAPS's own Carbs dialog (HardLimits.MAX_CARBS_DURATION_HOURS).
        private const val MAX_DURATION_HOURS = 10

        // Add one package per line for each external app you trust to prefill carbs.
        private val ALLOWED_CALLERS = setOf(
            "com.diabite.app"
            // e.g. also add "de.be10.carbcam" here if you use CarbCam too —
            // don't apply both this patch and the upstream CarbCam patch as-is,
            // they'd each try to create this same file. Merge the whitelist
            // entries into one file instead. See docs/aaps-integration/README.md.
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (resolveCaller() !in ALLOWED_CALLERS) { finish(); return }

        val carbs = intent.getIntExtra(EXTRA_CARBS, 0)
        val notes = intent.getStringExtra(EXTRA_NOTES) ?: ""
        if (carbs <= 0 || carbs > MAX_CARBS) { finish(); return }

        val forward = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        when (intent.action) {
            ACTION_OPEN_CARBS_DIALOG -> {
                val duration = intent.getIntExtra(EXTRA_DURATION, 0)
                if (duration < 0 || duration > MAX_DURATION_HOURS) { finish(); return }
                forward.putExtra(INTERNAL_ECARBS, carbs)
                forward.putExtra(INTERNAL_ECARBS_DURATION, duration)
                forward.putExtra(INTERNAL_ECARBS_NOTES, notes)
            }

            else                     -> {
                forward.putExtra(INTERNAL_CARBS, carbs)
                forward.putExtra(INTERNAL_NOTES, notes)
            }
        }
        startActivity(forward)
        finish()
    }

    /**
     * Android 14+: the system-attested launcher package, which a caller can't forge — it's
     * only reported when the caller opts in via ActivityOptions.setShareIdentityEnabled(true)
     * (DiaBite does), so callers that don't opt in are rejected. Older Android has no attested
     * source for a plain startActivity: callingPackage is null there, and referrer comes from
     * Intent.EXTRA_REFERRER, which any app can set — kept only as a weaker fallback.
     */
    private fun resolveCaller(): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) launchedFromPackage
        else callingPackage ?: referrer?.host
}
