plugins {
    id("lba.jacoco")
    id("lba.android.library")
    id("lba.hilt")
}

android {
    namespace = "fr.labonnealerte.core.data"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    api(libs.androidx.datastore.preferences)
    implementation(libs.timber)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
}
