plugins {
    id("lba.android.feature")
}

android {
    namespace = "fr.labonnealerte.feature.reglages"
}

dependencies {
    implementation(project(":core:push"))
    implementation(project(":core:notifications"))
    implementation(project(":core:database"))
    implementation(project(":core:network"))
    implementation(libs.androidx.work.runtime)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
