plugins {
    id("lba.android.feature")
}

android {
    namespace = "fr.labonnealerte.feature.communaute"
}

dependencies {
    testImplementation(libs.robolectric)
}
