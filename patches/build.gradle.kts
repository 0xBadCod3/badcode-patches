group = "app.professor.morphe-patches"

patches {
    about {
        name = "Professor's Morphe Patches"
        description = "Custom Morphe Patches by Professor."
        source = "https://github.com/rushiranpise/morphe-patches"
        author = "Professor"
        contact = "https://github.com"
        website = "https://github.com"
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
