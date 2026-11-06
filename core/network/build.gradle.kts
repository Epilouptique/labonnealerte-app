plugins {
    id("lba.jacoco")
    id("lba.android.library")
    alias(libs.plugins.kotlin.serialization)
    id("lba.hilt")
}

android {
    namespace = "fr.labonnealerte.core.network"
}

dependencies {
    api(project(":core:model"))
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx)
    api(libs.okhttp)
    implementation(libs.timber)
    testImplementation(libs.okhttp.mockwebserver)
}
