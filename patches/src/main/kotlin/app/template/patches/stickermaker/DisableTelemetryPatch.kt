package app.template.patches.stickermaker

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.STICKER_MAKER_COMPATIBILITY
import org.w3c.dom.Element

/**
 * Manifest patch for Sticker Maker: Disables FirebaseInitProvider,
 * ComponentDiscoveryService, AppMeasurement services, and DataTransport jobs.
 */
val disableTelemetryManifestPatch = resourcePatch(
    default = true
) {
    compatibleWith(STICKER_MAKER_COMPATIBILITY)

    execute {
        document("AndroidManifest.xml").use { doc ->
            val tags = listOf("provider", "receiver", "service")
            val targetKeywords = listOf(
                "com.google.android.gms.measurement",
                "com.google.android.datatransport",
                "com.google.firebase.sessions.SessionLifecycleService",
                "com.google.firebase.provider.FirebaseInitProvider",
                "com.google.firebase.components.ComponentDiscoveryService",
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

val stickerFirebaseAnalyticsGetInstanceFingerprint = Fingerprint(
    definingClass = "Lcom/google/firebase/analytics/FirebaseAnalytics;",
    name = "getInstance",
    returnType = "Lcom/google/firebase/analytics/FirebaseAnalytics;",
    parameters = listOf("Landroid/content/Context;"),
)

val stickerFirebaseAppInitializeAppFingerprint = Fingerprint(
    definingClass = "Lcom/google/firebase/FirebaseApp;",
    name = "initializeApp",
    returnType = "Lcom/google/firebase/FirebaseApp;",
    parameters = listOf("Landroid/content/Context;"),
)

val stickerFirebaseCrashlyticsGetInstanceFingerprint = Fingerprint(
    definingClass = "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;",
    name = "getInstance",
    returnType = "Lcom/google/firebase/crashlytics/FirebaseCrashlytics;",
    parameters = emptyList(),
)

/**
 * Bytecode patch for Sticker Maker: Completely neutralizes Firebase analytics,
 * crashlytics, and measurement telemetry across any app update.
 */
@Suppress("unused")
val disableTelemetryPatch = bytecodePatch(
    name = "Disable Analytics / Telemetry",
    description = "Disables all Firebase analytics, Crashlytics, and measurement telemetry dispatchers.",
    default = true,
) {
    compatibleWith(STICKER_MAKER_COMPATIBILITY)
    dependsOn(disableTelemetryManifestPatch)

    execute {
        val emptyListSmali = """
            invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
            move-result-object v0
            return-object v0
        """.trimIndent()

        // 1. Dynamic structural scan across all classes for Firebase / Measurement dispatchers
        classDefForEach { classDef ->
            // Stub ComponentRegistrar implementations
            if (classDef.interfaces.any { it == "Lcom/google/firebase/components/ComponentRegistrar;" }) {
                val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
                val getComponentsMethod = mutableClass.methods.firstOrNull {
                    it.name == "getComponents" && it.returnType == "Ljava/util/List;" && it.implementation != null
                }
                getComponentsMethod?.addInstructions(0, emptyListSmali)
            }

            // Stub FirebaseAnalytics event tracking methods
            if (classDef.type.startsWith("Lcom/google/firebase/analytics/")) {
                val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
                mutableClass.methods.forEach { method ->
                    if (method.implementation == null) return@forEach
                    if (method.name == "logEvent" || method.name == "a") {
                        if (method.returnType == "V") {
                            method.addInstructions(0, "return-void")
                        }
                    }
                }
            }

            // Stub Google AppMeasurement services / dispatchers
            if (classDef.type.startsWith("Lcom/google/android/gms/measurement/")) {
                val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
                mutableClass.methods.forEach { method ->
                    if (method.implementation == null) return@forEach
                    if (method.name == "logEvent" || method.name == "logEventInternal") {
                        if (method.returnType == "V") {
                            method.addInstructions(0, "return-void")
                        }
                    }
                }
            }

            // Neutralize custom internal telemetry loggers that forward events to Firebase
            val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
            mutableClass.methods.forEach { method ->
                if (method.implementation == null || method.name == "<init>" || method.name == "<clinit>") return@forEach
                if (method.returnType == "V" &&
                    method.implementation?.instructions?.any { instr ->
                        val s = instr.toString()
                        s.contains("Lcom/google/firebase/analytics/FirebaseAnalytics;") ||
                            s.contains("Lcom/google/android/gms/measurement/")
                    } == true
                ) {
                    method.addInstructions(0, "return-void")
                }
            }
        }

        // 2. Specific singleton entrypoint fingerprints
        stickerFirebaseAnalyticsGetInstanceFingerprint.matchOrNull()?.method?.let { method ->
            if (method.implementation != null) {
                method.addInstructions(
                    0,
                    """
                        const/4 v0, 0x0
                        return-object v0
                    """.trimIndent()
                )
            }
        }

        stickerFirebaseAppInitializeAppFingerprint.matchOrNull()?.method?.let { method ->
            if (method.implementation != null) {
                method.addInstructions(
                    0,
                    """
                        const/4 v0, 0x0
                        return-object v0
                    """.trimIndent()
                )
            }
        }

        stickerFirebaseCrashlyticsGetInstanceFingerprint.matchOrNull()?.method?.let { method ->
            if (method.implementation != null) {
                method.addInstructions(
                    0,
                    """
                        const/4 v0, 0x0
                        return-object v0
                    """.trimIndent()
                )
            }
        }
    }
}
