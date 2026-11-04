plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// R3 is the default playback engine after on-device listening tests.
// Use -Prubberband=false to build the previous Sonic implementation.
val useRubberBand = providers.gradleProperty("rubberband").orNull?.let {
    when (it.lowercase()) {
        "true" -> true
        "false" -> false
        else -> error("rubberband must be true or false")
    }
} ?: true

// Never store signing secrets in Gradle files or version control.
val releaseKeyStore = providers.environmentVariable("ANDROID_RELEASE_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("ANDROID_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANDROID_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANDROID_RELEASE_KEY_PASSWORD").orNull
val hasReleaseSigning = !releaseKeyStore.isNullOrBlank() &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

// An unsigned "release" APK is not suitable for distributing to friends.
if (gradle.startParameter.taskNames.any { it.contains("Release", ignoreCase = true) } &&
    !hasReleaseSigning) {
    error("Release signing variables are missing. Run tools/release/build-release.ps1.")
}

android {
    namespace = "com.wombatriy.androidplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wombatriy.androidplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0-rc1"

        buildConfigField("boolean", "WITH_RUBBERBAND", useRubberBand.toString())
        if (useRubberBand) {
            // First native experiment: modern 64-bit Android phones only.
            ndk { abiFilters.add("arm64-v8a") }
        }
    }

    buildFeatures { buildConfig = true }

    signingConfigs {
        if (hasReleaseSigning) {
            create("localRelease") {
                storeFile = file(requireNotNull(releaseKeyStore))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("localRelease")
        }
    }

    if (useRubberBand) {
        externalNativeBuild {
            cmake {
                path = file("src/main/cpp/CMakeLists.txt")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildToolsVersion = "35.0.0"
}

dependencies {
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.documentfile:documentfile:1.1.0")
    testImplementation("junit:junit:4.13.2")
}
