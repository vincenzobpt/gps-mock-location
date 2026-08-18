plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mocklocation.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.mocklocation.app"
        minSdk = 26
        targetSdk = 35
        // versionCode was left at 1 through the v1.1.0 and v1.2.0 tags, so an
        // APK never registered as an upgrade over the previous one. Realigned
        // here: one code per release, 1.0.0=1 … 1.3.0=4.
        versionCode = 4
        versionName = "1.3.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)

    // osmdroid — OpenStreetMap tiles without API keys
    implementation(libs.osmdroid.android)

    // Networking — OSRM routing API
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Tests — plain JVM, no Robolectric: SimulationEngine takes its
    // mock-location backend through MockLocationPort, so the engine's
    // integrator and its threading are exercised with a fake, never the real
    // platform providers.
    testImplementation(libs.junit)
}
