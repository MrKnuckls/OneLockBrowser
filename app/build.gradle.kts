plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.knuckls.onelockbrowser"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.knuckls.onelockbrowser"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            // Shared upload keystore with OneLock — REQUIRED: the browser consumes
            // OneLock's signature-protected rules provider, so both apps must be
            // signed with the same key.
            storeFile = rootProject.file(System.getenv("ONELOCK_KS") ?: "onelock-upload.keystore")
            storePassword = System.getenv("ONELOCK_KS_PASS")
            keyAlias = "onelock"
            keyPassword = System.getenv("ONELOCK_KS_PASS")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            isMinifyEnabled = false
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
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.webkit:webkit:1.12.0")
}
