plugins {
    id("org.jetbrains.kotlin.multiplatform")
}

kotlin {
    jvm {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
            testLogging { events("failed"); showStandardStreams = false }
            maxHeapSize = "1g"
        }
    }
    js {
        browser()
    }
    sourceSets {
        val jvmTest by getting {
            dependencies {
                implementation(project.dependencies.platform("org.junit:junit-bom:5.14.4"))
                implementation("org.junit.jupiter:junit-jupiter")
                runtimeOnly("org.junit.platform:junit-platform-launcher")
            }
        }
    }
}

// keep the old task name working for scripts and docs
tasks.register("test") { dependsOn("jvmTest") }
