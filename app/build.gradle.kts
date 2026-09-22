import java.util.Properties

plugins {
    id("com.android.application")
}

val playUploadProps = Properties().apply {
    val propsFile = file(System.getProperty("user.home") + "/.config/taipeisignal/play-upload.properties")
    if (propsFile.exists()) {
        propsFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.bryce.taipeisignalhud"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bryce.taipeisignalhud"
        minSdk = 23
        targetSdk = 35
        versionCode = 28
        versionName = "0.1.5-compact-highway-nosnap"
    }

    signingConfigs {
        create("release") {
            storeFile = file(playUploadProps.getProperty("storeFile"))
            storePassword = playUploadProps.getProperty("storePassword")
            keyAlias = playUploadProps.getProperty("keyAlias")
            keyPassword = playUploadProps.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.car.app:app:1.8.0-rc01")
    implementation("androidx.car.app:app-projected:1.8.0-rc01")
}
