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
val copyFigureAssets = tasks.register<Sync>("copyFigureAssets") {
    // B-08: the muscle map, the rigs and the credits that must ship with them (Content §5.1). Not part of the content loader.
    from(rootProject.layout.projectDirectory.dir("content/figures")) {
        include("muscle-map.json", "xnk-source/figure.min.json", "xnk-source/rig.min.json", "xnk-source/CREDITS.md", "xnk-source/LICENSE-*.txt")
    }
    into(layout.buildDirectory.dir("generated/content-assets/figures"))
}
// preBuild runs before every variant task (assets merge, lint, tests), so the copy is never skipped.
tasks.named("preBuild") { dependsOn(copyContentAssets, copyFigureAssets) }

android {
    namespace = "app.xnkfit"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.xnkfit"
        minSdk = 26 // PRD NF-4, Design §1
        targetSdk = 36 // Design §1 (S-02)
        versionCode = 1
        versionName = "0.1.0"
        // Drive backup (B-16): the owner's OAuth client id, passed at build time (-Pxnkfit.driveClientId=... or the
        // XNKFIT_DRIVE_CLIENT_ID env var). Empty by default: the repo is public and holds no client id
        // (Google-OAuth.md §3); an empty value turns every Drive feature into "belum tersedia".
        val driveClientId = (project.findProperty("xnkfit.driveClientId") as String?)
            ?: System.getenv("XNKFIT_DRIVE_CLIENT_ID") ?: ""
        buildConfigField("String", "DRIVE_OAUTH_CLIENT_ID", "\"" + driveClientId.replace("\"", "") + "\"")
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
        buildConfig = true
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
        // The strings are Indonesian, which has one plural form ("%d kkal", "%d hari"); the check assumes English plurals.
        disable += "PluralsCandidate"
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
    implementation(libs.play.services.auth)
    implementation(libs.health.connect)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.test.core)
    testImplementation(libs.room.testing)
}
