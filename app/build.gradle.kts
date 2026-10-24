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
        versionCode = 3
        versionName = "1.2"
    }

    // A fixed key so every new build installs as an update over the previous one.
    signingConfigs {
        create("glassplay") {
            storeFile = file("glassplay.keystore")
            storePassword = "glassplay"
            keyAlias = "glassplay"
            keyPassword = "glassplay"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("glassplay")
        }
        debug {
            signingConfig = signingConfigs.getByName("glassplay")
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
}
