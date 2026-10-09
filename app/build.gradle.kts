plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "ir.comicreader.fa"
    compileSdk = 35

    defaultConfig {
        applicationId = "ir.comicreader.fa"
        minSdk = 26
        targetSdk = 35
        // CI passes a strictly increasing version so devices accept updates.
        // Set these in gradle.properties (comickhan.versionCode / comickhan.versionName).
        versionCode = (project.findProperty("comickhan.versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("comickhan.versionName") as String?) ?: "1.0"
    }

    signingConfigs {
        create("release") {
            val path = System.getenv("KEYSTORE_FILE")
            if (!path.isNullOrBlank() && file(path).exists()) {
                storeFile = file(path)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the release key when provided (CI secrets); otherwise fall back to
            // debug signing so local/CI builds still produce an installable APK.
            val hasReleaseKey = !System.getenv("KEYSTORE_FILE").isNullOrBlank() &&
                file(System.getenv("KEYSTORE_FILE")!!).exists()
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.documentfile)
    implementation(libs.junrar)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.foundation)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    debugImplementation(libs.androidx.ui.tooling)
}
