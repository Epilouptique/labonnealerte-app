plugins {
    id("lba.android.feature")
}

android {
    namespace = "fr.labonnealerte.feature.kiosque"
}

dependencies {
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
