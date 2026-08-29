package app.template.patches.cloudflare.warp

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.WARP_COMPATIBILITY

@Suppress("unused")
val disableSslPinningPatch = bytecodePatch(
    name = "Disable SSL Pinning",
    description = "Bypasses OkHttp certificate pinning on Cloudflare API calls to allow TLS traffic inspection.",
    default = false,
) {
    compatibleWith(WARP_COMPATIBILITY)

    execute {
        mutableClassDefByOrNull("Lokhttp3/CertificatePinner;")?.methods?.forEach { method ->
            if (method.name.startsWith("check") && method.returnType == "V") {
                method.addInstructions(0, "return-void")
            }
        }
    }
}
