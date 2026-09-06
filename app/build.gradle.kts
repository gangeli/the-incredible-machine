plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "tim.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "tim.android"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.1"
    }

    signingConfigs {
        // Sideload-only signing key. It is checked in on purpose so that every build installs over
        // the previous one; it protects nothing (the app is not on any store).
        create("release") {
            storeFile = file("release.keystore")
            storePassword = System.getenv("TIM_KEYSTORE_PASSWORD") ?: "timtimtim"
            keyAlias = "tim"
            keyPassword = System.getenv("TIM_KEY_PASSWORD") ?: "timtimtim"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += listOf("META-INF/*.version", "META-INF/*.kotlin_module", "kotlin/**", "META-INF/versions/**", "DebugProbesKt.bin", "**/*.kotlin_builtins", "kotlin-tooling-metadata.json", "META-INF/version-control-info.textproto", "META-INF/com/android/build/gradle/app-metadata.properties")
        }
    }

    lint { abortOnError = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
