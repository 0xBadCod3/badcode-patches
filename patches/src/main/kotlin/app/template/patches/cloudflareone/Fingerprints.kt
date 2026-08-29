package app.template.patches.cloudflareone

import app.morphe.patcher.Fingerprint
import com.android.tools.smali.dexlib2.AccessFlags

// ─── Firebase Core & Analytics ──────────────────────────────────────────────

object FirebaseAppInitializeAppFingerprint : Fingerprint(
    definingClass = "Lcom/google/firebase/FirebaseApp;",
    name = "initializeApp",
)

object FirebaseCrashlyticsGetInstanceFingerprint : Fingerprint(
    definingClass = "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;",
    name = "getInstance",
    returnType = "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;",
)

object FirebaseAnalyticsGetInstanceFingerprint : Fingerprint(
    definingClass = "Lcom/google/firebase/analytics/FirebaseAnalytics;",
    name = "getInstance",
    returnType = "Lcom/google/firebase/analytics/FirebaseAnalytics;",
)

// ─── Cloudflare Analytics Service ───────────────────────────────────────────

object CloudflareAnalyticsServiceDispatchFingerprint : Fingerprint(
    returnType = "V",
    accessFlags = listOf(AccessFlags.PUBLIC, AccessFlags.STATIC, AccessFlags.FINAL),
    parameters = listOf("Lcom/cloudflare/app/domain/analytics/AnalyticsService;", "Landroid/os/Bundle;"),
    definingClass = "Lcom/cloudflare/app/domain/analytics/AnalyticsService;",
    name = "c",
)
