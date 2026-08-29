package app.template.patches.cloudflare.warp

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.WARP_COMPATIBILITY

@Suppress("unused")
val disableAnalyticsPatch = bytecodePatch(
    name = "Disable Analytics / Telemetry",
    description = "Disables all Cloudflare telemetry by no-oping the analytics bundle builder and the Firebase event dispatchers.",
    default = true,
) {
    compatibleWith(WARP_COMPATIBILITY)

    execute {
        // Layer 1: No-op static bundle builder on AnalyticsService
        mutableClassDefByOrNull("Lcom/cloudflare/app/domain/analytics/AnalyticsService;")?.methods?.forEach { method ->
            if (method.returnType == "V" && method.parameterTypes.map { it.toString() } == listOf(
                    "Lcom/cloudflare/app/domain/analytics/AnalyticsService;",
                    "Landroid/os/Bundle;"
                )
            ) {
                method.addInstructions(0, "return-void")
            }
        }

        // Layer 2: No-op Firebase event dispatcher lambdas
        classDefForEach { classDef ->
            if (!classDef.type.startsWith("Lcom/cloudflare/app/domain/analytics/AnalyticsService\$")) return@classDefForEach
            if (classDef.interfaces.none { it == "Lkotlin/jvm/functions/Function1;" }) return@classDefForEach

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
