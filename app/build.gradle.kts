import java.util.Properties

plugins {
    id("lba.android.application")
    id("lba.android.compose")
    id("lba.hilt")
    alias(libs.plugins.baselineprofile)
}

// Profil de reference : genere a la demande (gradlew :app:generateFossReleaseBaselineProfile, emulateur
// connecte) et enregistre dans src/fossRelease/generated/baselineProfiles. Jamais pendant un build normal
// (F-Droid, CI) : le fichier versionne suffit.
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
}

android {
    namespace = "fr.labonnealerte.app"

    defaultConfig {
        applicationId = "fr.labonnealerte.app"
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Adresse du service. Le build debug peut viser le serveur factice (voir buildTypes).
        buildConfigField("String", "API_BASE_URL", "\"https://labonnealerte.fr/\"")
        // Connexion Google (APP-22, flavor play) : ID client Web du site (= GOOGLE_CLIENT_ID du serveur).
        // Hors depot : propriete Gradle lbaGoogleWebClientId (~/.gradle/gradle.properties) ou variable
        // LBA_GOOGLE_WEB_CLIENT_ID. Vide : bouton Google masque.
        val googleWebClientId = (findProperty("lbaGoogleWebClientId") as String?) ?: System.getenv("LBA_GOOGLE_WEB_CLIENT_ID") ?: ""
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"" + googleWebClientId.trim() + "\"")
    }

    // Signature de release : proprietes HORS depot (C:\Dev\secrets\lba-app\signing.properties en local,
    // LBA_SIGNING_PROPERTIES en CI). Sans elles, l'APK release reste non signe (F-Droid, CI).
    val signingFile = (System.getenv("LBA_SIGNING_PROPERTIES") ?: "C:/Dev/secrets/lba-app/signing.properties").let { file(it) }
    if (signingFile.exists()) {
        val p = Properties().apply { signingFile.inputStream().use { load(it) } }
        signingConfigs.create("release") {
            storeFile = file(p.getProperty("storeFile"))
            storePassword = p.getProperty("storePassword")
            keyAlias = p.getProperty("keyAlias")
            keyPassword = p.getProperty("keyPassword")
            enableV1Signing = false
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    flavorDimensions += "distribution"
    productFlavors {
        // F-Droid / GitHub : aucune dependance proprietaire. Un flavor "play" pourra s'ajouter ici.
        create("foss") { dimension = "distribution" }
        // Play Store : ajoute la connexion Google (Credential Manager, services Google).
        create("play") { dimension = "distribution" }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            // Serveur factice sur la machine hote, vu depuis l'emulateur.
            buildConfigField("String", "FAKE_API_BASE_URL", "\"http://127.0.0.1:8787/\"")
            // Options d'affichage (test) : visibles en debug seulement (APP-23, D040).
            buildConfigField("boolean", "SHOW_TEST_OPTIONS", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            buildConfigField("String", "FAKE_API_BASE_URL", "\"\"")
            buildConfigField("boolean", "SHOW_TEST_OPTIONS", "false")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    // Le bloc de signature des dependances est illisible pour F-Droid : desactive.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/*.version", "DebugProbesKt.bin")
    }
}

dependencies {
    "playImplementation"(libs.androidx.credentials)
    "playImplementation"(libs.androidx.credentials.play)
    "playImplementation"(libs.googleid)
    implementation(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":core:notifications"))
    implementation(project(":core:push"))
    implementation(project(":feature:kiosque"))
    implementation(project(":feature:alerte"))
    implementation(project(":feature:comptes"))
    implementation(project(":feature:forum"))
    implementation(project(":feature:communaute"))
    implementation(project(":feature:echeances"))
    implementation(project(":feature:reglages"))
    implementation(project(":feature:onboarding"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    implementation(libs.compose.material3.windowsize)
    implementation(libs.timber)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
}
