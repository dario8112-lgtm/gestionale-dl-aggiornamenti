plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "it.dlsas.cassaforte"
    compileSdk = 35
    defaultConfig {
        applicationId = "it.dlsas.cassaforte"
        minSdk = 30; targetSdk = 35
        versionCode = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()
        versionName = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")
    }
    signingConfigs {
        create("dl") {
            storeFile = file(System.getenv("CDL_KEYSTORE") ?: "debug.jks")
            storePassword = System.getenv("CDL_KS_PASS") ?: "android"
            keyAlias = System.getenv("CDL_KEY_ALIAS") ?: "cassaforte"
            keyPassword = System.getenv("CDL_KS_PASS") ?: "android"
        }
    }
    buildTypes { getByName("release") { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("dl") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    packaging { resources { excludes += "META-INF/versions/9/OSGI-INF/MANIFEST.MF" } }
}
dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
}
