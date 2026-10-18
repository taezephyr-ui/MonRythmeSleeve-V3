plugins {
    id("com.android.application")
}

android {
    namespace = "fr.monrythmesleeve.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "fr.monrythmesleeve.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 21
        versionName = "2.1.0-beta"
    }
    buildTypes {
        getByName("debug") {
            // Beta independante: elle ne peut pas ecraser la V1 avec une autre signature debug.
            applicationIdSuffix = ".beta"
        }
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

