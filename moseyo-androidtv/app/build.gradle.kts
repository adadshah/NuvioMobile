import java.util.Properties

plugins {
    id("com.android.application")
}

val versionProps = Properties().apply {
    rootProject.file("version.properties").inputStream().use(::load)
}
fun env(key: String): String? = System.getenv(key)?.trim()?.takeIf { it.isNotEmpty() }
val keystorePath = env("MOSEYO_KEYSTORE_FILE")

android {
    namespace = "com.moseyo.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.moseyo.tv"
        minSdk = 21
        targetSdk = 36
        versionCode = versionProps.getProperty("versionCode").trim().toInt()
        versionName = versionProps.getProperty("versionName").trim()
        // The hosted Moseyo TV app (the same one the Hisense/LG TVs run).
        buildConfigField("String", "START_URL", "\"${env("MOSEYO_TV_URL") ?: "https://nuvio-tv.kidsafe.workers.dev/"}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            if (keystorePath != null) {
                storeFile = file(keystorePath)
                storePassword = env("MOSEYO_KEYSTORE_PASSWORD")
                keyAlias = env("MOSEYO_KEY_ALIAS")
                keyPassword = env("MOSEYO_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (keystorePath != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
