plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Enable R3 only for a local GPL evaluation APK:
// ./gradlew :app:assembleDebug -Prubberband=true
val useRubberBand = providers.gradleProperty("rubberband").orNull == "true"

android {
    namespace = "com.wombatriy.androidplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.wombatriy.androidplayer"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("boolean", "WITH_RUBBERBAND", useRubberBand.toString())
        if (useRubberBand) {
            // First native experiment: modern 64-bit Android phones only.
            ndk { abiFilters.add("arm64-v8a") }
        }
    }

    buildFeatures { buildConfig = true }

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
