plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.openlauncher.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.openlauncher.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 6
        versionName = "0.0.5"
        // Mapbox mobile SDKs require a public (pk.*) token. Supply it through
        // -PMAPBOX_ACCESS_TOKEN=... or the MAPBOX_ACCESS_TOKEN environment variable.
        resValue(
            "string",
            "mapbox_access_token",
            providers.gradleProperty("MAPBOX_ACCESS_TOKEN")
                .orElse(providers.environmentVariable("MAPBOX_ACCESS_TOKEN"))
                .getOrElse("pk.eyJ1IjoibWFwYm94IiwiYSI6ImNpejY4M29iazA2Z2gycXA4N2pmbDZmangifQ.A62N2nA6w2220P9nPxie7g"),
        )
    }

    buildTypes {
        debug {
            // Default signing config for normal device testing
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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

    // Keep native libraries uncompressed and 16 KB ZIP-aligned in APKs/AABs.
    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Network — weather
    implementation("com.squareup.retrofit2:retrofit:2.12.0")
    implementation("com.squareup.retrofit2:converter-gson:2.12.0")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.37.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    // JSON serialization
    implementation("com.google.code.gson:gson:2.13.1")

    // Mapbox SDKs (Maps & Search SDK)
    // -------------------------------------------------------------------------
    // 1. Mapbox Maps SDK & Compose Extensions
    implementation("com.mapbox.maps:android-ndk27:11.14.0")
    implementation("com.mapbox.extension:maps-compose-ndk27:11.14.0")

    // 2. Mapbox Search SDK (Autofill, POI, Category search)
    implementation("com.mapbox.search:mapbox-search-android-ui-ndk27:2.14.0")
    implementation("com.mapbox.search:autofill-ndk27:2.14.0")

    // Route generation also works without a network connection when routing
    // tiles are included in the downloaded TileStore regions.
    implementation("com.mapbox.navigationcore:navigation-ndk27:3.11.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
