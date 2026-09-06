plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

// The browser build: the shared game core compiled to JavaScript plus a thin Canvas/WebAudio shell.
kotlin {
    js {
        outputModuleName.set("tim")
        browser()
        binaries.executable()
    }
    sourceSets {
        val jsMain by getting {
            dependencies { implementation(project(":core")) }
        }
    }
}

// Stamp the service worker with the commit so a new deploy replaces the cached game.
val buildStamp: String = try {
    providers.exec { commandLine("git", "rev-parse", "--short", "HEAD"); isIgnoreExitValue = true }.standardOutput.asText.get().trim().ifEmpty { System.currentTimeMillis().toString() }
} catch (e: Exception) { System.currentTimeMillis().toString() }

tasks.withType<ProcessResources>().configureEach {
    filesMatching(listOf("sw.js", "index.html")) { filter { line -> line.replace("@VERSION@", buildStamp) } }
}

// The whole game as one plain script (see kotlin.js.ir.output.granularity in gradle.properties) next to
// the static shell: no webpack, no npm. Serve or publish build/webdist as-is.
val webDist by tasks.registering(Sync::class) {
    description = "Assembles the browser version into build/webdist"
    val compile = tasks.named("compileProductionExecutableKotlinJs")
    dependsOn(compile, "jsProcessResources")
    from(compile.map { it.outputs.files }) { include("*.js"); exclude("*.map") }
    from(layout.buildDirectory.dir("processedResources/js/main"))
    into(layout.buildDirectory.dir("webdist"))
    doLast {
        val dir = layout.buildDirectory.dir("webdist").get().asFile
        val js = File(dir, "tim.js")
        // strip the sourcemap pointer; the map is not shipped
        js.writeText(js.readText().lines().filterNot { it.startsWith("//# sourceMappingURL=") }.joinToString("\n"))
        // minify when esbuild is on the PATH (CI installs it; locally it is optional)
        val esbuild = System.getenv("PATH").split(File.pathSeparator).map { File(it, "esbuild") }.firstOrNull { it.canExecute() }
        if (esbuild != null) {
            val min = File(dir, "tim.min.js")
            val rc = ProcessBuilder(esbuild.path, js.path, "--minify", "--target=es2017", "--log-level=warning", "--outfile=" + min.path).inheritIO().start().waitFor()
            if (rc == 0 && min.length() > 0) { js.delete(); min.renameTo(js) } else logger.warn("esbuild failed ($rc); shipping the unminified script")
        } else logger.lifecycle("esbuild not found; shipping the unminified script")
        println("web build: " + dir.listFiles()?.sortedBy { it.name }?.joinToString { "${it.name} (${it.length() / 1024} KB)" })
    }
}
