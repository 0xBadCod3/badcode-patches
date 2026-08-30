package app.template.patches.cloudflareone

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.CLOUDFLARE_ONE_AGENT_COMPATIBILITY
import app.template.patches.shared.Constants.CLOUDFLARE_WARP_COMPATIBILITY
import org.w3c.dom.Element

/**
 * Manifest patch: Disables Google Analytics telemetry, Measurement services,
 * and background data transport jobs in AndroidManifest.xml.
 */
val removeFirebaseManifestPatch = resourcePatch(
    default = true
) {
    compatibleWith(CLOUDFLARE_ONE_AGENT_COMPATIBILITY, CLOUDFLARE_WARP_COMPATIBILITY)

    execute {
        document("AndroidManifest.xml").use { doc ->
            val tags = listOf("provider", "receiver", "service")
            val targetKeywords = listOf(
                "com.google.android.gms.measurement",
                "com.google.android.datatransport",
                "com.google.firebase.sessions.SessionLifecycleService",
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
 * Bytecode patch: Completely disables Firebase telemetry, analytics, and Crashlytics,
 * while safely stubbing DeviceRegistrationManager so device key generation and VPN
 * registration succeed without requiring Firebase or Google Play Services.
 */
@Suppress("unused")
val removeFirebasePatch = bytecodePatch(
    name = "Remove Firebase & Telemetry",
    description = "Completely disables Firebase analytics, Crashlytics, and measurement telemetry while ensuring device registration and VPN operate independently.",
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

        val singleEmptyStringSmali = """
            const-string v0, ""
            invoke-static {v0}, Lio/reactivex/Single;->f(Ljava/lang/Object;)Lio/reactivex/internal/operators/single/SingleJust;
            move-result-object v0
            return-object v0
        """.trimIndent()

        val taskEmptyStringSmali = """
            const-string v0, ""
            invoke-static {v0}, Lcom/google/android/gms/tasks/Tasks;->e(Ljava/lang/Object;)Lcom/google/android/gms/tasks/Task;
            move-result-object v0
            return-object v0
        """.trimIndent()

        // 1. Dynamic ComponentRegistrar neutralization:
        // Scans all classes in the APK that implement ComponentRegistrar and forces
        // getComponents() to return an empty list for telemetry components.
        val targetRegistrars = listOf(
            "CrashlyticsRegistrar",
            "AnalyticsConnectorRegistrar",
            "FirebaseSessionsRegistrar",
            "FirebasePerfRegistrar",
        )
        classDefForEach { classDef ->
            if (classDef.interfaces.any { it == "Lcom/google/firebase/components/ComponentRegistrar;" }) {
                if (targetRegistrars.any { classDef.type.contains(it) }) {
                    val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
                    mutableClass.methods.firstOrNull { it.name == "getComponents" && it.returnType == "Ljava/util/List;" }
                        ?.addInstructions(0, emptyListSmali)
                }
            }
        }

        // 2. Stub DeviceRegistrationManager to bypass Firebase Token / Installation ID dependencies:
        // Dynamically scans for DeviceRegistrationManager across packages to emit Single.just("") immediately.
        // This ensures the device keypair and Cloudflare /reg endpoint registration complete successfully.
        classDefForEach { classDef ->
            if (classDef.type.contains("DeviceRegistrationManager") || classDef.type.contains("domain/warp")) {
                val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
                mutableClass.methods.forEach { method ->
                    if (method.returnType == "Lio/reactivex/Single;") {
                        if (method.parameterTypes.isEmpty() || method.parameterTypes == listOf("Z")) {
                            method.addInstructions(0, singleEmptyStringSmali)
                        }
                    }
                }
            }
        }

        // 3. Stub FirebaseMessaging.getToken() and FirebaseInstallations.getId() to complete immediately with empty Task
        mutableClassDefByOrNull("Lcom/google/firebase/messaging/FirebaseMessaging;")?.methods?.forEach { method ->
            if (method.name == "getToken" && method.returnType == "Lcom/google/android/gms/tasks/Task;") {
                method.addInstructions(0, taskEmptyStringSmali)
            }
        }
        mutableClassDefByOrNull("Lcom/google/firebase/installations/FirebaseInstallations;")?.methods?.forEach { method ->
            if (method.name == "getId" && method.returnType == "Lcom/google/android/gms/tasks/Task;") {
                method.addInstructions(0, taskEmptyStringSmali)
            }
        }

        // 4. Safe FirebaseCrashlytics neutralization:
        // Returns a safe dummy object so callers don't encounter NPE.
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

        // 5. Safe FirebaseAnalytics neutralization:
        // No-op all void telemetry methods on FirebaseAnalytics (logEvent, setUserProperty, etc.)
        mutableClassDefByOrNull("Lcom/google/firebase/analytics/FirebaseAnalytics;")?.methods?.forEach { method ->
            if (method.returnType == "V" && method.name != "<init>") {
                method.addInstructions(0, "return-void")
            }
        }

        // 6. No-op Cloudflare AnalyticsService static dispatcher if present
        CloudflareAnalyticsServiceDispatchFingerprint.matchOrNull()?.method?.addInstructions(0, "return-void")

        // 7. Scan and neutralize anonymous inner class dispatchers sending to GMS / Firebase
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
