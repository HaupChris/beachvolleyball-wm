pluginManagement {
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

rootProject.name = "ZahnputzTracker"
include(":core", ":app")

// Second, independent app (own applicationId and data) living in pushups/.
include(":pushups-core", ":pushups-app")
project(":pushups-core").projectDir = file("pushups/core")
project(":pushups-app").projectDir = file("pushups/app")
