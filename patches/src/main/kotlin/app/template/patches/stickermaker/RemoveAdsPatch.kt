package app.template.patches.stickermaker

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.STICKER_MAKER_COMPATIBILITY
import org.w3c.dom.Element

/**
 * Manifest patch: Disables ad-related activities, services, content providers,
 * and ad permissions in AndroidManifest.xml for Sticker Maker.
 */
val removeAdsManifestPatch = resourcePatch(
    default = true
) {
    compatibleWith(STICKER_MAKER_COMPATIBILITY)

    execute {
        document("AndroidManifest.xml").use { doc ->
            val tags = listOf("activity", "provider", "service", "receiver")
            val adKeywords = listOf(
                "com.google.android.gms.ads",
                "com.inmobi.ads",
                "com.my.target",
                "com.bytedance.sdk.openadsdk",
                "com.facebook.ads",
            )

            tags.forEach { tagName ->
                val nodes = doc.getElementsByTagName(tagName)
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i) as? Element ?: continue
                    val name = node.getAttribute("android:name")
                    if (adKeywords.any { name.lowercase().contains(it) }) {
                        node.setAttribute("android:enabled", "false")
                    }
                }
            }

            // Remove ad permissions
            val permissionNodes = doc.getElementsByTagName("uses-permission")
            val adPermissionKeywords = listOf(
                "com.google.android.gms.permission.AD_ID",
                "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
                "android.permission.ACCESS_ADSERVICES_AD_ID",
                "android.permission.ACCESS_ADSERVICES_TOPICS",
            )
            for (i in 0 until permissionNodes.length) {
                val node = permissionNodes.item(i) as? Element ?: continue
                val name = node.getAttribute("android:name")
                if (adPermissionKeywords.contains(name)) {
                    node.parentNode?.removeChild(node)
                }
            }
        }
    }
}

/**
 * Bytecode patch: Disables all in-app ads by safely no-oping
 * ad managers, preventing background ad requests, banners, and interstitials.
 */
@Suppress("unused")
val removeAdsPatch = bytecodePatch(
    name = "Remove Advertisements",
    description = "Disables all in-app advertisements across AdMob, Pangle, InMobi, MyTarget, and Facebook Audience Network.",
    default = true,
) {
    compatibleWith(STICKER_MAKER_COMPATIBILITY)
    dependsOn(removeAdsManifestPatch)

    execute {
        // Dynamic structural scan across all classes in the app for ad mediation and ad loaders
        classDefForEach { classDef ->
            // 1. Intercept all ZJSoft ad manager methods
            if (classDef.type.contains("zjsoft/admob")) {
                val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
                mutableClass.methods.forEach { method ->
                    val callbackParamIndex = method.parameterTypes.indexOfFirst { it.contains("admob/e;") }
                    if (callbackParamIndex >= 0) {
                        val pReg = "p${callbackParamIndex + (if ((method.accessFlags.toInt() and 8) != 0) 0 else 1)}"
                        method.addInstructions(
                            0,
                            """
                                if-eqz $pReg, :cond_skip_ad
                                const/4 v0, 0x0
                                invoke-interface {$pReg, v0}, Lcom/zjsoft/admob/e;->a(Z)V
                                :cond_skip_ad
                                return-void
                            """.trimIndent()
                        )
                    }
                }
            }

            // 2. Intercept AdView / Interstitial / Rewarded ad loaders
            if (classDef.type.startsWith("Lcom/google/android/gms/ads/")) {
                val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
                mutableClass.methods.forEach { method ->
                    if (method.name == "loadAd" || method.name == "load" || method.name == "show") {
                        if (method.returnType == "V") {
                            method.addInstructions(0, "return-void")
                        }
                    }
                }
            }

            // 3. Intercept InMobi, Pangle, FAN, MyTarget initialization
            if (classDef.type.startsWith("Lcom/inmobi/") ||
                classDef.type.startsWith("Lcom/bytedance/sdk/openadsdk/") ||
                classDef.type.startsWith("Lcom/facebook/ads/") ||
                classDef.type.startsWith("Lcom/my/target/")
            ) {
                val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
                mutableClass.methods.forEach { method ->
                    if (method.name == "init" || method.name == "initialize" || method.name == "initSdk") {
                        if (method.returnType == "V") {
                            method.addInstructions(0, "return-void")
                        }
                    }
                }
            }
        }
    }
}
