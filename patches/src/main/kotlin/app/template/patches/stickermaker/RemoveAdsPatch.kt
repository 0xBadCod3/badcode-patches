package app.template.patches.stickermaker

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.template.patches.shared.Constants.STICKER_MAKER_COMPATIBILITY
import org.w3c.dom.Element

/**
 * Manifest patch: Disables ad-related activities, services, content providers,
 * ad permissions, and PairIP license check activities in AndroidManifest.xml.
 */
val removeAdsManifestPatch = resourcePatch(
    default = true
) {
    compatibleWith(STICKER_MAKER_COMPATIBILITY)

    execute {
        document("AndroidManifest.xml").use { doc ->
            // 1. Point <application> directly to the genuine StickerApplication class if wrapped by PairIP
            val applicationNodes = doc.getElementsByTagName("application")
            if (applicationNodes.length > 0) {
                val appElem = applicationNodes.item(0) as? Element
                val currentName = appElem?.getAttribute("android:name")
                if (currentName?.contains("pairip") == true) {
                    appElem.setAttribute(
                        "android:name",
                        "customstickermaker.whatsappstickers.personalstickersforwhatsapp.base.StickerApplication"
                    )
                }
            }

            // 2. Disable ad-related and pairip-related activities, services, receivers, and providers
            val tags = listOf("activity", "provider", "service", "receiver")
            val adKeywords = listOf(
                "com.google.android.gms.ads",
                "com.inmobi.ads",
                "com.my.target",
                "com.bytedance.sdk.openadsdk",
                "com.facebook.ads",
                "com.pairip.licensecheck",
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

            // 3. Remove ad permissions
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
 * ad managers, preventing background ad requests, banners, and interstitials,
 * and ensures modified app launches smoothly by bypassing PairIP startup exits.
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
        // Dynamic structural scan across all classes in the app for anti-tamper signature checks, ad mediation, loaders, and PairIP
        classDefForEach { classDef ->
            // 1. Neutralize all anti-tamper signature checks across all app activities & helper classes
            val isAntiTamperClass = classDef.methods.any { m ->
                m.implementation?.instructions?.any { instr ->
                    val s = instr.toString()
                    s.contains("System.exit returned normally") ||
                        s.contains("signatures:[Landroid/content/pm/Signature;") ||
                        s.contains("signingInfo:Landroid/content/pm/SigningInfo;") ||
                        s.contains("toCharsString") ||
                        s.contains("getApkContentsSigners")
                } == true
            }
            if (isAntiTamperClass) {
                val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
                mutableClass.methods.forEach { method ->
                    if (method.implementation == null || method.name == "<clinit>") return@forEach
                    if (method.returnType == "V") {
                        method.addInstructions(0, "return-void")
                    } else if (method.returnType == "Z") {
                        method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
                    } else if (method.returnType == "Ljava/lang/String;") {
                        method.addInstructions(0, "const-string v0, \"\"\nreturn-object v0")
                    }
                }
            }

            // 2. Neutralize central process termination helpers (LC9/d;->b, killProcess, System.exit, Runtime.halt/exit)
            val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
            mutableClass.methods.forEach { method ->
                if (method.implementation == null || method.name == "<init>" || method.name == "<clinit>") return@forEach
                val hasKillCall = method.implementation?.instructions?.any { instr ->
                    val s = instr.toString()
                    s.contains("Landroid/os/Process;->killProcess(I)V") ||
                        s.contains("Ljava/lang/System;->exit(I)V") ||
                        s.contains("Ljava/lang/Runtime;->exit(I)V") ||
                        s.contains("Ljava/lang/Runtime;->halt(I)V") ||
                        s.contains("Landroid/os/Process;->sendSignal(II)V")
                } == true
                if (hasKillCall) {
                    if (method.returnType == "Ljava/lang/RuntimeException;") {
                        method.addInstructions(
                            0,
                            """
                                new-instance v0, Ljava/lang/RuntimeException;
                                invoke-direct {v0, p1}, Ljava/lang/RuntimeException;-><init>(Ljava/lang/String;)V
                                return-object v0
                            """.trimIndent()
                        )
                    } else if (method.returnType == "Ljava/lang/Object;" || method.returnType == "Llb/l;") {
                        method.addInstructions(
                            0,
                            """
                                sget-object v0, Llb/l;->a:Llb/l;
                                return-object v0
                            """.trimIndent()
                        )
                    } else if (method.returnType == "V") {
                        method.addInstructions(0, "return-void")
                    } else if (method.returnType == "Z") {
                        method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
                    }
                }
            }

            // 3. Bypass PairIP installer and license check shutdowns on launch
            if (classDef.type.contains("pairip/licensecheck/LicenseClient") ||
                classDef.type.contains("pairip/application/Application") ||
                classDef.type.contains("pairip/licensecheck/LicenseActivity")
            ) {
                mutableClass.methods.forEach { method ->
                    if (method.implementation == null) return@forEach
                    if (method.name == "checkLicense" || method.name == "initializeLicenseCheck" || method.name == "onCreate") {
                        if (method.returnType == "V") {
                            method.addInstructions(0, "return-void")
                        }
                    } else if (method.name == "performLocalInstallerCheck" || method.name == "isLicensed") {
                        if (method.returnType == "Z") {
                            method.addInstructions(0, "const/4 v0, 0x1\nreturn v0")
                        }
                    } else if (method.name == "attachBaseContext") {
                        method.addInstructions(
                            0,
                            """
                                invoke-super {p0, p1}, Lcustomstickermaker/whatsappstickers/personalstickersforwhatsapp/base/StickerApplication;->attachBaseContext(Landroid/content/Context;)V
                                return-void
                            """.trimIndent()
                        )
                    }
                }
            }

            // 4. Intercept all ZJSoft ad manager methods
            if (classDef.type.contains("zjsoft/admob")) {
                mutableClass.methods.forEach { method ->
                    if (method.implementation == null) return@forEach
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

            // 5. Intercept AdView / Interstitial / Rewarded ad loaders
            if (classDef.type.startsWith("Lcom/google/android/gms/ads/")) {
                mutableClass.methods.forEach { method ->
                    if (method.implementation == null) return@forEach
                    if (method.name == "loadAd" || method.name == "load" || method.name == "show") {
                        if (method.returnType == "V") {
                            method.addInstructions(0, "return-void")
                        }
                    }
                }
            }

            // 6. Intercept InMobi, Pangle, FAN, MyTarget initialization
            if (classDef.type.startsWith("Lcom/inmobi/") ||
                classDef.type.startsWith("Lcom/bytedance/sdk/openadsdk/") ||
                classDef.type.startsWith("Lcom/facebook/ads/") ||
                classDef.type.startsWith("Lcom/my/target/")
            ) {
                mutableClass.methods.forEach { method ->
                    if (method.implementation == null) return@forEach
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
