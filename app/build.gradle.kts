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
        versionCode = 32
        versionName = "3.0.2-beta"
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
    implementation("com.google.android.gms:play-services-wearable:18.2.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

