group = "app.badcode.morphe-patches"

patches {
    about {
        name = "BadCode's Morphe Patches"
        description = "Custom Morphe Patches by 0xBadCod3."
        source = "https://github.com/0xBadCod3/badcode-patches"
        author = "0xBadCod3"
        contact = "https://github.com/0xBadCod3"
        website = "https://morphe.software/add-source?github=0xBadCod3/badcode-patches"
        license = "GPLv3"
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
    }
}

val patchListGeneratorClasspath: Configuration = configurations.create("patchListGeneratorClasspath")

dependencies {
    compileOnly(libs.gson)
    patchListGeneratorClasspath(libs.gson)
}

tasks {
    register<JavaExec>("generatePatchesList") {
        description = "Build patch with patch list"
        dependsOn(build)
        classpath = sourceSets["main"].runtimeClasspath + patchListGeneratorClasspath
        mainClass.set("app.morphe.util.PatchListGeneratorKt")
    }
}
