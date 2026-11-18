plugins { id("com.android.application") }

val ks = rootDir.resolve("teclatv.keystore")

android {
    namespace = "app.teclatv"
    compileSdk = 36
    defaultConfig {
        applicationId = "app.teclatv"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        if (ks.exists()) create("release") {
            storeFile = ks; storePassword = "teclatv-keystore"
            keyAlias = "teclatv"; keyPassword = "teclatv-keystore"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }
    dependenciesInfo { includeInApk = false; includeInBundle = false }
}
