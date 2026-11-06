plugins { id("com.android.application") }
android {
    namespace = "fr.monrythmesleeve.watch"
    compileSdk = 35
    defaultConfig { applicationId = "fr.monrythmesleeve.app"; minSdk = 30; targetSdk = 34; versionCode = 32; versionName = "3.0.2-beta" }
    buildTypes { getByName("debug") { applicationIdSuffix = ".beta" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies { implementation("com.google.android.gms:play-services-wearable:18.2.0") }
