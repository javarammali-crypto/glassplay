import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.glassplay.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.glassplay.app"
        minSdk = 26
        targetSdk = 35
        // GitHub builds stamp their release number (v1, v2, …) into the app so it can tell when
        // a newer release is out. versionCode follows the build run, so it only ever goes up.
        val release = System.getenv("APP_VERSION")?.toIntOrNull() ?: 0
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 4
        versionName = if (release > 0) "v$release" else "dev"
        buildConfigField("int", "RELEASE_NUMBER", release.toString())
        buildConfigField("String", "UPDATE_REPO", "\"javarammali-crypto/glassplay\"")

        // Firebase (accounts and synced watch history). These values identify the Firebase
        // project and aren't secret; they live in firebase.properties at the repo root.
        val firebase = Properties().apply {
            rootProject.file("firebase.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        fun firebaseValue(key: String) = "\"" + (firebase.getProperty(key) ?: "").trim() + "\""
        buildConfigField("String", "FIREBASE_API_KEY", firebaseValue("apiKey"))
        buildConfigField("String", "FIREBASE_APP_ID", firebaseValue("appId"))
        buildConfigField("String", "FIREBASE_PROJECT_ID", firebaseValue("projectId"))
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", firebaseValue("webClientId"))
    }

    // Releases are signed with a private key that only GitHub has (stored as repository
    // secrets), so nobody else can publish a build that installs over GlassPlay. Builds made
    // elsewhere use Android's debug key and can't update a released app.
    val keystorePath = System.getenv("SIGNING_KEYSTORE_PATH")
    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "glassplay"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-util")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-exoplayer-dash:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    // Software audio decoders (AC3, E-AC3, DTS, TrueHD…) for streams the device can't decode itself.
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")

    // Torrent streaming for add-ons that return infoHash streams (Torrentio and similar).
    val libtorrent = "2.1.0-39"
    implementation("org.libtorrent4j:libtorrent4j:$libtorrent")
    implementation("org.libtorrent4j:libtorrent4j-android-arm:$libtorrent")
    implementation("org.libtorrent4j:libtorrent4j-android-arm64:$libtorrent")
    implementation("org.libtorrent4j:libtorrent4j-android-x86_64:$libtorrent")

    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Accounts and synced watch history.
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    // Sign in with Google.
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}
