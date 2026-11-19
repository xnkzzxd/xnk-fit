// Macrobenchmark and baseline profile generator for the app (B-19, Design §10). Runs on a device or emulator only:
// see README.md. CI compiles it; it does not run it (no device on the runner).
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "app.xnkfit.benchmark"
    compileSdk = 36

    defaultConfig {
        minSdk = 26 // the API 26 emulator of Design §10
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    experimentalProperties["android.experimental.self-instrumenting"] = true

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

baselineProfile {
    // The phone or emulator that is plugged in (adb). No Gradle managed device: it would download a system image
    // on every run and the CI runner has no KVM budget for it.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.test.ext.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}
