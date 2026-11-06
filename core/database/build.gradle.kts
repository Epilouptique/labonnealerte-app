plugins {
    id("lba.android.library")
    id("lba.hilt")
    alias(libs.plugins.room)
}

android {
    namespace = "fr.labonnealerte.core.database"
    // Schemas exportes lus par MigrationTestHelper (tests unitaires Robolectric).
    (sourceSets as NamedDomainObjectContainer<*>).named("test").configure {
        (this as com.android.build.api.dsl.AndroidSourceSet).assets.directories.add("$projectDir/schemas")
    }
}

dependencies {
    api(project(":core:model"))
    api(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.junit)
}

room {
    schemaDirectory("$projectDir/schemas")
}
