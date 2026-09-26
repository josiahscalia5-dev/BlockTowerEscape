plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.blocktower.escape"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.blocktower.escape"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "0.4.0-screen4"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    androidResources {
        noCompress += listOf("jpg", "png", "ttf")
    }
}

// No third-party libraries: the game is plain Kotlin + android.graphics.
dependencies {
}
