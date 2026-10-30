import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Signing values come from local.properties (the CI workflow writes this file).
// If it is missing (plain local build) the release build falls back to the debug key.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.align.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.align.app"
        minSdk = 26
        targetSdk = 35
        // NOTE: the GitHub workflow rewrites the next two lines with sed. Keep this exact format.
        versionCode = 3
        versionName = "1.1.1"
    }

    signingConfigs {
        create("release") {
            val storePath = localProps.getProperty("signing.storeFile")
            if (storePath != null) {
                storeFile = rootProject.file(storePath)
                storePassword = localProps.getProperty("signing.storePassword")
                keyAlias = localProps.getProperty("signing.keyAlias")
                keyPassword = localProps.getProperty("signing.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // R8 (code shrinking + optimisation) makes the release build noticeably smoother. Resources are not
            // shrunk on purpose (illustrations / drawables are looked up at runtime).
            isMinifyEnabled = true
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (localProps.getProperty("signing.storeFile") != null) {
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

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")

    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    // Installs the baseline profile (src/main/baseline-prof.txt) so Compose code is precompiled on first run.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    // Renders the downloaded unDraw SVG illustrations (assets/illustrations) to bitmaps.
    implementation("com.caverock:androidsvg-aar:1.4")
}
