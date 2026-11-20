pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "med-clinic-platform"

include(
    ":libs:common",
    ":libs:license",
    ":libs:vendor-protocol",
    ":vendor-platform:backend",
)
