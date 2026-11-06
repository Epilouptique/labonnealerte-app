plugins {
    id("lba.android.feature")
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "fr.labonnealerte.feature.alerte"
}

dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
