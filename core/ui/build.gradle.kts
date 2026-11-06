plugins {
    id("lba.android.library")
    alias(libs.plugins.roborazzi)
    id("lba.android.compose")
}

android {
    namespace = "fr.labonnealerte.core.ui"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.coil.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    api(libs.androidx.browser)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
