plugins {
    `kotlin-dsl`
}

java { toolchain { languageVersion.set(JavaLanguageVersion.of(17)) } }

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "lba.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = "lba.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = "lba.android.compose"
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "lba.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("hilt") {
            id = "lba.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("jacoco") {
            id = "lba.jacoco"
            implementationClass = "JacocoConventionPlugin"
        }
        register("jvmLibrary") {
            id = "lba.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
    }
}
