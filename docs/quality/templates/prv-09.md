<!-- Kritik issue skeleton for PRV-09 (Privacy & Data Protection) — generated from library/framework.json v0.1.0.
     Fill every {placeholder}; title convention below; labels: quality, legal, core + the surface label + milestone. -->

Title: [Quality] PRV-09 {surface}: ambient exposure of emotional content ({finding_summary})

**Criterion**: PRV-09 Ambient on-device exposure of sensitive content (PRV/ambient-exposure)
**Surface**: {surface}
**Observed level**: {observed_level}/4 · **Target level**: {target_level}/4

### Evidence
{notification_snapshot_or_lock_findings_with_paths}

### Risk
Impact {impact}/5 × Likelihood {likelihood}/5. {risk_narrative}

### Remediation
- [ ] {neutralize_notification_payloads}
- [ ] {shield_app_switcher_snapshot}
- [ ] {add_optional_app_lock}

### Acceptance criteria
- No notification reveals emotional content on the lock screen
- Backgrounded app shows a redacted snapshot on {surface}
- Level {target_level} anchor met: {anchor_text}

---
_Criterion: **PRV-09 · Ambient on-device exposure of sensitive content** (`ambient-exposure`) — see [criteria reference](../criteria/index.md)._
_Question: Is emotional content shielded from people near the device: notification previews carry none of it, the app-switcher snapshot hides sensitive screens, and an optional app lock protects entry?_
_References: [OWASP MASVS v2 — MASVS-PLATFORM-3 (sensitive data in the user interface: screenshots, notifications, keyboard cache)](https://mas.owasp.org/MASVS/) · [Android Developers: WindowManager.LayoutParams — FLAG_SECURE (exclude window content from screenshots and non-secure displays)](https://developer.android.com/reference/android/view/WindowManager.LayoutParams) · [Apple Developer Documentation: UIKit — applicationDidEnterBackground(_:) (prepare the UI before the system snapshots it for the app switcher)](https://developer.apple.com/documentation/uikit) · [Apple Developer Documentation: LocalAuthentication — Authenticating a user with Face ID or Touch ID](https://developer.apple.com/documentation/localauthentication)_