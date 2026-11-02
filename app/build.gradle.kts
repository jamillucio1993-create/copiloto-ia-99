plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()
val signingFile = System.getenv("COPILOTO_KEYSTORE_PATH")
val signingPassword = System.getenv("COPILOTO_KEYSTORE_PASSWORD")
val signingAlias = System.getenv("COPILOTO_KEY_ALIAS")
val signingKeyPassword = System.getenv("COPILOTO_KEY_PASSWORD")
val hasReleaseSigning = listOf(signingFile, signingPassword, signingAlias, signingKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "br.com.copilotoia99"
    compileSdk = 35
    defaultConfig {
        applicationId = "br.com.copilotoia99"
        minSdk = 31
        targetSdk = 35
        // GitHub Actions run numbers are monotonically increasing for this repository.
        versionCode = if (ciRun != null) 1000 + ciRun else 12
        versionName = if (ciRun != null) "2.0.$ciRun" else "1.9.0-updates"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (hasReleaseSigning) {
            create("copilotoRelease") {
                storeFile = file(signingFile!!)
                storePassword = signingPassword
                keyAlias = signingAlias
                keyPassword = signingKeyPassword
            }
        }
    }
    buildTypes {
        getByName("release") {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("copilotoRelease")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation("com.google.mlkit:text-recognition:16.0.1")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.1")
}
