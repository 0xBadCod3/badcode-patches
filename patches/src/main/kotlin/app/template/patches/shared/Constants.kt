package app.template.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {

    val CLOUDFLARE_ONE_AGENT_COMPATIBILITY = Compatibility(
        name = "Cloudflare One Agent",
        packageName = "com.cloudflare.cloudflareoneagent",
        appIconColor = 0xF48120,
        apkFileType = ApkFileType.APK,
        targets = listOf(
            AppTarget(version = "2.5.5", versionCode = 5640),
            AppTarget(version = "any")
        )
    )

    val CLOUDFLARE_WARP_COMPATIBILITY = Compatibility(
        name = "1.1.1.1: Faster & Safer Internet",
        packageName = "com.cloudflare.onedotonedotonedotone",
        appIconColor = 0xF48120,
        apkFileType = ApkFileType.APK,
        targets = listOf(
            AppTarget(version = "6.38.9", versionCode = 5641),
            AppTarget(version = "6.38.8", versionCode = 5431),
            AppTarget(version = "any")
        )
    )

    val WARP_COMPATIBILITY = CLOUDFLARE_WARP_COMPATIBILITY

    val STICKER_MAKER_COMPATIBILITY = Compatibility(
        name = "Sticker Maker for WhatsApp",
        packageName = "customstickermaker.whatsappstickers.personalstickersforwhatsapp",
        appIconColor = 0x25D366,
        apkFileType = ApkFileType.APK,
        targets = listOf(
            AppTarget(version = "1.292.79", versionCode = 79000),
            AppTarget(version = "any")
        )
    )
}
