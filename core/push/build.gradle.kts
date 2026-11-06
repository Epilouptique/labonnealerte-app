plugins {
    id("lba.android.library")
    id("lba.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "fr.labonnealerte.core.push"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:notifications"))
    implementation(project(":core:network"))
    implementation(project(":core:database"))
    implementation(libs.unifiedpush.connector)
    implementation(libs.tink.android)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.timber)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.androidx.work.testing)
}
