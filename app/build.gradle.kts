plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Top-level *.json of content/ only (the figures and notes are not part of the loader). The APK deflates the
// JSON (about 10x), so the files are copied byte for byte instead of minified; see docs/Content-Update.md §8.
val copyContentAssets = tasks.register<Sync>("copyContentAssets") {
    from(rootProject.layout.projectDirectory.dir("content")) { include("*.json") }
    into(layout.buildDirectory.dir("generated/content-assets/content"))
}
// preBuild runs before every variant task (assets merge, lint, tests), so the copy is never skipped.
tasks.named("preBuild") { dependsOn(copyContentAssets) }

android {
    namespace = "app.xnkfit"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.xnkfit"
        minSdk = 26 // PRD NF-4, Design §1
        targetSdk = 36 // Design §1 (S-02)
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // No signingConfig on purpose: the release key is added through CI secrets later.
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        // "Newer version available" checks go stale by themselves and would turn CI red with no code change.
        // Version bumps are done on purpose, one dependency at a time, with a row in Design §12.
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
        // Same family: it fires as soon as the CI runner has a newer platform than our target (it did, API 37).
        // targetSdk is a recorded decision in Design §1 (S-02), raised on purpose at release time, not by a lint date.
        disable += "OldTargetApi"
    }

    testOptions {
        // Robolectric needs merged resources and the manifest (Design §12).
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        // Bundled content (B-04): copied from the repo's content/ at build time, never duplicated by hand.
        // Same bytes as the files the content repo publishes, so file hashes match the manifest.
        getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/content-assets"))
        // MigrationTestHelper reads the exported schema JSON as assets under Robolectric (Design §13).
        // Debug only: the release APK does not carry the schemas.
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

room {
    // Schema JSON per release feeds the migration tests (Design §3, NF-5). Used from B-03.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.room.testing)
}
