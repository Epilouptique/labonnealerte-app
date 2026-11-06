pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "LaBonneAlerte"

include(":app")
include(":core:model")
include(":core:network")
include(":core:database")
include(":core:data")
include(":core:ui")
include(":core:notifications")
include(":core:push")
include(":feature:kiosque")
include(":feature:alerte")
include(":feature:comptes")
include(":feature:forum")
include(":feature:communaute")
include(":feature:echeances")
include(":feature:reglages")
include(":feature:onboarding")
include(":baselineprofile")
