plugins {
    id("lba.android.feature")
}

android {
    namespace = "fr.labonnealerte.feature.echeances"
}

dependencies {
    testImplementation(libs.robolectric)
}
