// App-level build configuration for StreamCaster.
// This is where we configure Android SDK versions, product flavors,
// dependencies, and build features.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.port80.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.port80.app"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Product flavors: "foss" for F-Droid (no Google services),
    // "gms" for Google Play Store (with Google services).
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") {
            dimension = "distribution"
            applicationIdSuffix = ".foss"
        }
        create("gms") {
            dimension = "distribution"
        }
    }

    buildTypes {
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

    // Resolve duplicate files from dependencies
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // ── Compose (BOM manages consistent versions) ──
    val composeBom = platform(libs.compose.bom)
    implementation(composeBom)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    // ── Activity & Navigation ──
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)

    // ── Hilt dependency injection ──
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // ── AndroidX core ──
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    // ── RootEncoder for RTMP streaming ──
    implementation(libs.rootencoder)

    // ── ACRA crash reporting ──
    implementation(libs.acra.http)
    implementation(libs.acra.dialog)

    // ── Testing ──
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.arch.core.testing)
}
