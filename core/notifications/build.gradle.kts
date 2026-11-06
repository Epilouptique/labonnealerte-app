plugins {
    id("lba.android.library")
    id("lba.hilt")
}

android {
    namespace = "fr.labonnealerte.core.notifications"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:database"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.timber)
    testImplementation(libs.robolectric)
    testImplementation(project(":core:network"))
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.test.junit)
}
