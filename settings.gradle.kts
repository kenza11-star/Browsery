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
        // GeckoView dipublikasikan di Maven Mozilla.
        maven(url = "https://maven.mozilla.org/maven2/")
    }
}

rootProject.name = "Browsery"
include(":app")
