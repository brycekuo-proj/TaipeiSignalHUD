plugins {
    id("com.android.application")
}

android {
    namespace = "com.bryce.taipeisignalhud"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.bryce.taipeisignalhud"
        minSdk = 23
        targetSdk = 35
        versionCode = 18
        versionName = "0.0.18-roadtest"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
