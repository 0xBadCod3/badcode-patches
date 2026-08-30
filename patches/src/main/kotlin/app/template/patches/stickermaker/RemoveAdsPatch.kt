package app.template.patches.stickermaker

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.fingerprint.methodFingerprint
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.STICKER_MAKER_COMPATIBILITY
import org.w3c.dom.Element

/**
 * Manifest patch: Future-proof disabling of all ad-related activities, services,
 * content providers, receivers, and ad permissions in AndroidManifest.xml.
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
                "admob",
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

            // Remove all ad tracking permissions
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
 * Fingerprint matching ZJSoft AdMob Mediation loader:
 * Lcom/zjsoft/admob/b;->b(Landroid/app/Activity;ZLcom/zjsoft/admob/e;)V
 */
val zjsoftAdmobLoaderFingerprint = methodFingerprint {
    custom = { method, _ ->
        method.definingClass.contains("zjsoft/admob") &&
            method.parameterTypes.any { it.contains("admob") }
    }
}

/**
 * Fingerprint matching Google Mobile Ads initialization:
 */
val mobileAdsInitFingerprint = methodFingerprint {
    custom = { method, _ ->
        method.definingClass == "Lcom/google/android/gms/ads/MobileAds;" &&
            (method.name == "initialize" || method.parameterTypes.any { it.contains("admob") })
    }
}

/**
 * Fingerprint matching InMobi SDK init:
 */
val inMobiSdkInitFingerprint = methodFingerprint {
    custom = { method, _ ->
        method.definingClass.startsWith("Lcom/inmobi/") && method.name == "init"
    }
}

/**
 * Fingerprint matching Facebook Audience Network init:
 */
val audienceNetworkInitFingerprint = methodFingerprint {
    custom = { method, _ ->
        method.definingClass.startsWith("Lcom/facebook/ads/") && method.name == "initialize"
    }
}

/**
 * Fingerprint matching ByteDance / Pangle TTAdSdk init:
 */
val pangleSdkInitFingerprint = methodFingerprint {
    custom = { method, _ ->
        method.definingClass.startsWith("Lcom/bytedance/sdk/openadsdk/") &&
            (method.name == "init" || method.name == "start")
    }
}

/**
 * Bytecode patch: Future-proof ad neutralization across AdMob, Pangle, InMobi,
 * MyTarget, and Facebook Audience Network by no-oping loaders and SDK entrypoints.
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
        // 1. Structural dynamic scan across all classes in the app for ad mediation and ad loaders
        classes.forEach { classDef ->
            // Intercept all ZJSoft ad manager methods
            if (classDef.type.contains("zjsoft/admob")) {
                classDef.methods.forEach { method ->
                    val callbackParamIndex = method.parameterTypes.indexOfFirst { it.contains("admob/e;") }
                    if (callbackParamIndex >= 0) {
                        // Notify callback immediately with false (ad not loaded)
                        val pReg = "p${callbackParamIndex + (if (method.accessFlags and 8 != 0) 0 else 1)}"
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

            // Intercept AdView / Interstitial / Rewarded ad loaders
            if (classDef.type.startsWith("Lcom/google/android/gms/ads/")) {
                classDef.methods.forEach { method ->
                    if (method.name == "loadAd" || method.name == "load" || method.name == "show") {
                        if (method.returnType == "V") {
                            method.addInstructions(0, "return-void")
                        }
                    }
                }
            }

            // Intercept InMobi, Pangle, FAN, MyTarget initialization
            if (classDef.type.startsWith("Lcom/inmobi/") ||
                classDef.type.startsWith("Lcom/bytedance/sdk/openadsdk/") ||
                classDef.type.startsWith("Lcom/facebook/ads/") ||
                classDef.type.startsWith("Lcom/my/target/")
            ) {
                classDef.methods.forEach { method ->
                    if (method.name == "init" || method.name == "initialize" || method.name == "initSdk") {
                        if (method.returnType == "V") {
                            method.addInstructions(0, "return-void")
                        }
                    }
                }
            }
        }

        // 2. Target specific fingerprints as secondary layer
        zjsoftAdmobLoaderFingerprint.result?.let { result ->
            result.mutableMethod.addInstructions(
                0,
                """
                    if-eqz p2, :cond_skip_ad
                    const/4 v0, 0x0
                    invoke-interface {p2, v0}, Lcom/zjsoft/admob/e;->a(Z)V
                    :cond_skip_ad
                    return-void
                """.trimIndent()
            )
        }

        mobileAdsInitFingerprint.result?.let { result ->
            result.mutableMethod.addInstructions(0, "return-void")
        }

        inMobiSdkInitFingerprint.result?.let { result ->
            result.mutableMethod.addInstructions(0, "return-void")
        }

        audienceNetworkInitFingerprint.result?.let { result ->
            result.mutableMethod.addInstructions(0, "return-void")
        }

        pangleSdkInitFingerprint.result?.let { result ->
            result.mutableMethod.addInstructions(0, "return-void")
        }
    }
}
