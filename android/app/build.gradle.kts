plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.fintrack"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.fintrack.sms"
        minSdk = 26
        targetSdk = 34
        versionCode = 3
        versionName = "0.3.0"
    }

    // A stable signing key lets you install updates over the old app without losing data.
    // CI passes it through environment variables (see .github/workflows/android.yml).
    val keystorePath = System.getenv("FINTRACK_KEYSTORE")
    signingConfigs {
        if (keystorePath != null) {
            create("stable") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("FINTRACK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("FINTRACK_KEY_ALIAS")
                keyPassword = System.getenv("FINTRACK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("stable") ?: signingConfigs.getByName("debug")
        }
        debug {
            signingConfigs.findByName("stable")?.let { signingConfig = it }
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    testImplementation("junit:junit:4.13.2")
}
