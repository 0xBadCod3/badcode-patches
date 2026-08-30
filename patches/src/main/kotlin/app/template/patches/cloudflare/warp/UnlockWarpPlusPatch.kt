package app.template.patches.cloudflare.warp

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.WARP_COMPATIBILITY

/**
 * Spoof WARP+ Unlimited — UI unlock via AccountData constructor intercept.
 *
 * Dynamically identifies the WarpPlusState parameter on any constructor of AccountData,
 * ensuring complete future-proof compatibility across all versions of 1.1.1.1.
 */
@Suppress("unused")
val unlockWarpPlusPatch = bytecodePatch(
    name = "Spoof WARP+ Unlimited UI",
    description = "Forces WarpPlusState to UNLIMITED on every AccountData instance by intercepting the primary constructor before the account type field is written.",
    default = true,
) {
    compatibleWith(WARP_COMPATIBILITY)

    execute {
        classDefForEach { classDef ->
            if (classDef.type.contains("AccountData") || classDef.type.contains("warpapi")) {
                val mutableClass = mutableClassDefByOrNull(classDef.type) ?: return@classDefForEach
                mutableClass.methods.forEach { method ->
                    if (method.name == "<init>") {
                        val warpStateIndex = method.parameterTypes.indexOfFirst { it.contains("WarpPlusState;") }
                        if (warpStateIndex >= 0) {
                            val warpStateType = method.parameterTypes[warpStateIndex].toString()
                            val reg = warpStateIndex + 1
                            method.addInstructions(
                                0,
                                "sget-object p$reg, $warpStateType->UNLIMITED:$warpStateType",
                            )
                        }
                    }
                }
            }
        }
    }
}
