// Generation du profil de reference (demarrage + kiosque) sur emulateur ou appareil :
//   gradlew :app:generateFossReleaseBaselineProfile
// Le resultat est versionne dans app/src/fossRelease/generated/baselineProfiles/ ; les builds
// normaux (F-Droid, CI) ne lancent jamais ce module (automaticGenerationDuringBuild = false).
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "fr.labonnealerte.baselineprofile"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
        targetSdk = 37
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":app"
    flavorDimensions += "distribution"
    productFlavors {
        create("foss") { dimension = "distribution" }
    }
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro)
}
