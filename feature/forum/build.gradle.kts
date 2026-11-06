plugins {
    id("lba.android.feature")
}

android {
    namespace = "fr.labonnealerte.feature.forum"
}

dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Les controles de depot (encodage, collisions de ressources) lisent des fichiers hors de ce
// module : Gradle ne les voit pas comme entrees et sauterait le test. On le relance toujours.
tasks.withType<Test>().configureEach { outputs.upToDateWhen { false } }
