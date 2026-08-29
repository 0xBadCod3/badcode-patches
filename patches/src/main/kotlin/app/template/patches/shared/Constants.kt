package app.template.patches.shared

import app.morphe.patcher.patch.ApkFileType
import app.morphe.patcher.patch.AppTarget
import app.morphe.patcher.patch.Compatibility

object Constants {

    val CLOUDFLARE_ONE_AGENT_COMPATIBILITY = Compatibility(
        name = "Cloudflare One Agent",
        packageName = "com.cloudflare.cloudflareoneagent",
        appIconColor = 0xF38020,
        apkFileType = ApkFileType.APK,
        targets = listOf(
            AppTarget(version = "any")
        )
    )

    val CLOUDFLARE_WARP_COMPATIBILITY = Compatibility(
        name = "Cloudflare WARP",
        packageName = "com.cloudflare.onedotonedotonedotone",
        appIconColor = 0xF38020,
        apkFileType = ApkFileType.APK,
        targets = listOf(
            AppTarget(version = "any")
        )
    )
}
