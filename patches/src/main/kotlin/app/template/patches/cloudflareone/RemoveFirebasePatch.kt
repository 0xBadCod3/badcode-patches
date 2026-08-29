package app.template.patches.cloudflareone

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.CLOUDFLARE_ONE_AGENT_COMPATIBILITY
import app.template.patches.shared.Constants.CLOUDFLARE_WARP_COMPATIBILITY
import org.w3c.dom.Element

/**
 * Manifest patch: Disables all Firebase, Google Measurement, and DataTransport
 * ContentProviders, BroadcastReceivers, and Services in AndroidManifest.xml.
 *
 * This stops Android OS from initializing Firebase via FirebaseInitProvider
 * and removes all background processes/alarms (e.g. FirebaseInstanceIdReceiver).
 */
val removeFirebaseManifestPatch = resourcePatch(
    default = true
) {
    compatibleWith(CLOUDFLARE_ONE_AGENT_COMPATIBILITY, CLOUDFLARE_WARP_COMPATIBILITY)

    execute {
        document("AndroidManifest.xml").use { doc ->
            val tags = listOf("provider", "receiver", "service")
            val targetKeywords = listOf(
                "com.google.firebase",
                "com.google.android.gms.measurement",
                "com.google.android.datatransport",
                "com.cloudflare.app.domain.fcm",
            )

            tags.forEach { tagName ->
                val nodes = doc.getElementsByTagName(tagName)
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as? Element ?: continue
                    val name = node.getAttribute("android:name")
                    if (targetKeywords.any { name.contains(it) }) {
                        node.setAttribute("android:enabled", "false")
                    }
                }
            }
        }
    }
}

/**
 * Bytecode patch: Completely and future-proofly strips Firebase execution and telemetry.
 *
 * 1. Dynamically scans and short-circuits ANY class implementing ComponentRegistrar
 *    (Crashlytics, Analytics, Perf, Sessions, Messaging, RemoteConfig, etc.).
 * 2. Neutralizes FirebaseCrashlytics (provides a dummy instance on getInstance() and
 *    no-ops all logging/reporting methods so no NPE or crash occurs if called).
 * 3. Neutralizes FirebaseAnalytics (no-ops logEvent and all telemetry setters).
 * 4. Neutralizes FirebaseApp.initializeApp().
 * 5. No-ops Cloudflare's internal AnalyticsService event builders and dispatchers.
 */
@Suppress("unused")
val removeFirebasePatch = bytecodePatch(
    name = "Remove Firebase & Telemetry",
    description = "Completely disables Firebase initialization, background receivers, registrars, and analytics telemetry.",
    default = true,
) {
    compatibleWith(CLOUDFLARE_ONE_AGENT_COMPATIBILITY, CLOUDFLARE_WARP_COMPATIBILITY)
    dependsOn(removeFirebaseManifestPatch)

    execute {
        val emptyListSmali = """
            invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
            move-result-object v0
            return-object v0
        """.trimIndent()

        // 1. Dynamic ComponentRegistrar neutralization:
        // Scans all classes in the APK that implement ComponentRegistrar and forces
        // getComponents() to return an empty list. Future-proof against any added Firebase modules.
        classDefForEach { classDef ->
            if (classDef.interfaces.any { it == "Lcom/google/firebase/components/ComponentRegistrar;" }) {
                val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
                mutableClass.methods.firstOrNull { it.name == "getComponents" && it.returnType == "Ljava/util/List;" }
                    ?.addInstructions(0, emptyListSmali)
            }
        }

        // 2. Safe FirebaseCrashlytics neutralization:
        // Returns a safe dummy object so callers don't encounter NPE if FirebaseApp is disabled.
        FirebaseCrashlyticsGetInstanceFingerprint.matchOrNull()?.method?.addInstructions(
            0,
            """
                new-instance v0, Lcom/google/firebase/crashlytics/FirebaseCrashlytics;
                const/4 v1, 0x0
                invoke-direct {v0, v1}, Lcom/google/firebase/crashlytics/FirebaseCrashlytics;-><init>(Lcom/google/firebase/crashlytics/internal/common/CrashlyticsCore;)V
                return-object v0
            """.trimIndent(),
        )

        // No-op all void methods on FirebaseCrashlytics (recordException, log, setUserId, setCustomKey, etc.)
        mutableClassDefByOrNull("Lcom/google/firebase/crashlytics/FirebaseCrashlytics;")?.methods?.forEach { method ->
            if (method.returnType == "V" && method.name != "<init>") {
                method.addInstructions(0, "return-void")
            }
        }

        // 3. Safe FirebaseAnalytics neutralization:
        // No-op all void telemetry methods on FirebaseAnalytics (logEvent, setUserProperty, etc.)
        mutableClassDefByOrNull("Lcom/google/firebase/analytics/FirebaseAnalytics;")?.methods?.forEach { method ->
            if (method.returnType == "V" && method.name != "<init>") {
                method.addInstructions(0, "return-void")
            }
        }

        // 4. Short-circuit FirebaseApp.initializeApp
        FirebaseAppInitializeAppFingerprint.matchAllOrNull()?.forEach { match ->
            val method = match.method
            if (method.returnType == "V") {
                method.addInstructions(0, "return-void")
            } else if (method.returnType.startsWith("L")) {
                method.addInstructions(
                    0,
                    """
                    const/4 v0, 0x0
                    return-object v0
                    """.trimIndent(),
                )
            }
        }

        // 5. No-op Cloudflare AnalyticsService static dispatcher if present
        CloudflareAnalyticsServiceDispatchFingerprint.matchOrNull()?.method?.addInstructions(0, "return-void")

        // 6. Scan and neutralize anonymous inner class dispatchers sending to GMS / Firebase
        classDefForEach { classDef ->
            if (!classDef.type.startsWith("Lcom/cloudflare/app/domain/analytics/AnalyticsService$")) {
                return@classDefForEach
            }

            if (classDef.interfaces.none { it == "Lkotlin/jvm/functions/Function1;" }) {
                return@classDefForEach
            }

            val targets = classDef.methods.filter { method ->
                method.name == "invoke" &&
                    method.returnType == "Ljava/lang/Object;" &&
                    method.parameterTypes.size == 1 &&
                    method.implementation?.instructions?.any { instruction ->
                        val s = instruction.toString()
                        s.contains("gms/internal/measurement") || s.contains("firebase")
                    } == true
            }

            if (targets.isEmpty()) return@classDefForEach

            val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
            targets.forEach { target ->
                mutableClass.methods.firstOrNull { candidate ->
                    candidate.name == target.name &&
                        candidate.returnType == target.returnType &&
                        candidate.parameterTypes.map { it.toString() } ==
                        target.parameterTypes.map { it.toString() }
                }?.addInstructions(
                    0,
                    """
                        sget-object p1, Lkotlin/Unit;->a:Lkotlin/Unit;
                        return-object p1
                    """.trimIndent(),
                )
            }
        }
    }
}
