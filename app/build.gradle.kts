import java.security.MessageDigest
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.pulsestream.app"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.pulsestream.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 153
        versionName = "0.14.5"
        // Android arm64 only: no armeabi-v7a / x86 native libs in the APK.
        ndk {
            abiFilters += "arm64-v8a"
        }
        // Sentry DSN — a client ingest key (public by design, send-only).
        // Passed at build time via -PSENTRY_DSN=... like the gate key.
        buildConfigField(
            "String",
            "SENTRY_DSN",
            "\"${providers.gradleProperty("SENTRY_DSN").orNull ?: ""}\""
        )
        // Locked-addon gate key — addon.plavio.site refuses every request
        // without it, so the house addon works only inside this app.
        // Passed at build time via -PAVIO_GATE_KEY=...; never committed.
        buildConfigField(
            "String",
            "AVIO_GATE_KEY",
            "\"${providers.gradleProperty("AVIO_GATE_KEY").orNull ?: ""}\""
        )
        // Avio account backend (Supabase). The anon key is a public
        // client key by design; row-level security guards the data.
        // Passed at build time via -PAVIO_SUPABASE_URL=... like the DSN.
        buildConfigField(
            "String",
            "AVIO_SUPABASE_URL",
            "\"${providers.gradleProperty("AVIO_SUPABASE_URL").orNull ?: ""}\""
        )
        buildConfigField(
            "String",
            "AVIO_SUPABASE_ANON_KEY",
            "\"${providers.gradleProperty("AVIO_SUPABASE_ANON_KEY").orNull ?: ""}\""
        )
    }

    // Publishing is explicit and must never silently use an unsigned/debug artifact.
    if (providers.gradleProperty("avioPublish").orNull == "true") {
        listOf("AVIO_SIGNING_STORE", "AVIO_SIGNING_STORE_PASSWORD",
            "AVIO_SIGNING_KEY_ALIAS", "AVIO_SIGNING_KEY_PASSWORD").forEach { key ->
            require(!providers.gradleProperty(key).orNull.isNullOrBlank()) { "Publishing requires $key" }
        }
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false // Update advisories are reviewed, not silently auto-upgraded.
    }
    // Build #24: okhttp-dnsoverhttps 5.3.2 and jspecify 1.0.0 both ship
    // META-INF/versions/9/OSGI-INF/MANIFEST.MF — duplicate at
    // :app:mergeMobileReleaseJavaResource. It is an OSGi bundle manifest,
    // unused at runtime; exclude it.
    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF"
        }
    }
    signingConfigs {
        // Optional owner signing; no machine-specific keystore is required to build.
        val signingStore = providers.gradleProperty("AVIO_SIGNING_STORE").orNull
        if (!signingStore.isNullOrBlank()) {
            create("avioOwner") {
                storeFile = file(signingStore)
                storePassword = providers.gradleProperty("AVIO_SIGNING_STORE_PASSWORD").orNull
                keyAlias = providers.gradleProperty("AVIO_SIGNING_KEY_ALIAS").orNull
                keyPassword = providers.gradleProperty("AVIO_SIGNING_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.findByName("avioOwner") ?: signingConfigs.getByName("debug")
        }
        release {
            signingConfig = signingConfigs.findByName("avioOwner")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    flavorDimensions += "device"
    productFlavors {
        create("mobile") {
            dimension = "device"
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
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("org.checkerframework:checker-qual:3.42.0")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.decoder)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.okhttp)
    // CloudStream (CS3): pin the RESOLVED OkHttp runtime to the 5.3.2 that
    // NiceHttp 0.4.18 was compiled against (same mechanism as the TV port).
    // Without this the runtime silently falls back to 4.12.0 and plugin HTTP
    // calls can throw NoSuchMethodError. The constraint is on
    // `implementation`, so okhttp core resolves 5.3.2 on BOTH compile and
    // runtime classpaths; the catalog `okhttp` version (4.12.0) now governs
    // only the declared artifact, not the resolved one.
    constraints {
        implementation("com.squareup.okhttp3:okhttp") { version { strictly(libs.versions.okhttpRuntime.get()) } }
        // CloudStream (CS3): same treatment for kotlinx-coroutines. The
        // vendored runtime AAR was compiled against coroutines 1.11.0 and
        // calls BuildersKt.runBlockingK, which exists only in 1.11.0+ — a
        // silent NoSuchMethodError at runtime if resolution ever drops below
        // it. Strictly pins the floor so a conflict fails the build instead
        // of dying on the phone. Verified 2026-11-22: 5 AAR classes reference
        // runBlockingK, and 1.11.0 renamed runBlocking to that JVM name.
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core") { version { strictly(libs.versions.coroutines.get()) } }
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android") { version { strictly(libs.versions.coroutines.get()) } }
    }
    // CloudStream (CS3) runtime: vendored AAR + the libraries plugins link
    // against (same set as the TV port; versions match Infuse/TV exactly).
    implementation(files("libs/cloudstream-runtime-api-4.8.0-3496e5f.aar"))
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.datetime)
    implementation("com.github.Blatzar:NiceHttp:0.4.18")
    implementation(libs.ksoup.jvm)
    implementation(libs.conscrypt.android)
    implementation(libs.fuzzywuzzy)
    // Kyant Backdrop — liquid-glass (Nuvio-style) refraction + blur.
    // 1.0.6: Kotlin 2.3.10 matches this project; its drawBackdrop /
    // lens / blur / vibrancy API is the same as Nuvio's 2.0.1 usage.
    // 2.0.1 needs Compose 1.12 / Kotlin 2.4.10 — NOT compatible with
    // this project's Compose BOM 2024.10.01 (1.7.x), so do not upgrade
    // the version without also upgrading the Compose BOM.
    implementation("io.github.kyant0:backdrop:1.0.6")
    implementation(libs.rhino)
    implementation(libs.tmdb.java)
    implementation(libs.cryptography.core)
    implementation(libs.cryptography.provider.optimal)
    implementation(kotlin("reflect"))
    // Sentry crash reports (owner's order 2026-10-23; not Google).
    implementation("io.sentry:sentry-android:8.14.0")
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    implementation(libs.nanohttpd)
    // Background episode-release checks (WorkManager, battery-friendly periodic work).
    implementation(libs.androidx.work.runtime.ktx)
    // Google Drive sync via AccountManager + REST API (no Play Services needed)
    // (user's order: replaces Supabase with Google login + Drive sync)
    // No Firebase / Google analytics at all (owner's order
    // 2026-10-22): install + activity counting runs on the owner's
    // own open-source backend (see AppStats). Crash capture is the
    // in-app catcher in PulseApp, fully local.
    // YouTube trailer extraction for the Trailers tab (same as ShortsBox).
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.5") {
        // Same exclusion as the TV port: NewPipeExtractor's rhino-engine
        // conflicts with the rhino 1.8.1 added for CloudStream above.
        exclude(group = "org.mozilla", module = "rhino-engine")
    }
    ksp(libs.room.compiler)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

// Exact artifact inventory complements notices and the owner's license review.
tasks.register("writeMobileDependencyInventory") {
    group = "verification"
    description = "Write mobile runtime dependency coordinates and SHA-256 checksums"
    doLast {
        val output = layout.buildDirectory.file("reports/mobile-dependencies.tsv").get().asFile
        output.parentFile.mkdirs()
        val artifacts = configurations.getByName("mobileDebugRuntimeClasspath")
            .resolvedConfiguration.resolvedArtifacts.sortedBy { it.moduleVersion.id.toString() }
        output.bufferedWriter().use { writer ->
            writer.appendLine("coordinate\tartifact\tsha256")
            artifacts.forEach { artifact ->
                val digest = MessageDigest.getInstance("SHA-256")
                    .digest(artifact.file.readBytes()).joinToString("") { "%02x".format(it.toInt() and 255) }
                writer.appendLine("${artifact.moduleVersion.id}\t${artifact.file.name}\t$digest")
            }
        }
        println("Mobile dependency inventory: ${output.relativeTo(project.rootDir)}")
    }
}
