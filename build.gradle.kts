plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
}

subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    // Analyse statique : regles par defaut + config/detekt/detekt.yml (ajustements motives).
    apply(plugin = "dev.detekt")
    extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
        buildUponDefaultConfig.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        parallel.set(true)
        // Constats du code existant au 08/10/2026, figes (D023) : seul le nouveau code doit etre propre.
        baseline.set(file("detekt-baseline.xml"))
    }
}

// Build reproductible : pas d'horodatage dans les archives.
allprojects {
    // Le connecteur UnifiedPush tire Tink (JVM) : la doc officielle demande la variante Android
    // (https://unifiedpush.org/kdoc/connector/, docs/specs-verifiees.md A.1).
    val tinkAndroid = rootProject.libs.tink.android.get().toString()
    configurations.configureEach {
        resolutionStrategy {
            force(tinkAndroid)
            dependencySubstitution {
                substitute(module("com.google.crypto.tink:tink")).using(module(tinkAndroid))
            }
        }
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
}
