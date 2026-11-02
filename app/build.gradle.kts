plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.thalzeronlabs.ondevicellm"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.thalzeronlabs.ondevicellm"
        minSdk = 26
        targetSdk = 36
        versionCode = 5
        versionName = "0.2.3"
    }

    // Release APKs on GitHub are signed with the Thalzeron Labs demo key (showcase apps only, never Google Play),
    // which the release workflow reads from the repository's Actions secrets; without them it generates a throwaway
    // key (see .github/workflows/release.yml). No key is stored in this repository. Without those variables, a local
    // release build falls back to the debug key so `./gradlew :app:assembleRelease` still produces an installable APK.
    val demoKeystore = System.getenv("DEMO_KEYSTORE_FILE")?.let(::file)?.takeIf { it.isFile }
    signingConfigs {
        if (demoKeystore != null) {
            create("demo") {
                storeFile = demoKeystore
                storePassword = System.getenv("DEMO_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("DEMO_KEY_ALIAS")
                keyPassword = System.getenv("DEMO_KEY_PASSWORD") ?: System.getenv("DEMO_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("demo") ?: signingConfigs.getByName("debug")
            // Phones only: every 64-bit ARM phone, and a smaller download. Debug builds keep x86_64 for emulators.
            ndk { abiFilters += "arm64-v8a" }
        }
        // A release build (minified, same native code) that installs next to the published app, so it can be
        // measured on a phone without uninstalling the release and its downloaded models.
        create("staging") {
            initWith(getByName("release"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-staging"
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += "release"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        // Robolectric (Room, Compose UI and Android framework tests on the JVM) needs the merged resources.
        unitTests.isIncludeAndroidResources = true
    }
}

roborazzi {
    // The README screenshots: `./gradlew :app:recordRoborazziDebug` rewrites them, CI verifies them.
    outputDir.set(file("../docs/screenshots"))
}

ksp {
    // The database schema is checked in, so every future migration can be reviewed and tested against it.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(project(":llama"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
