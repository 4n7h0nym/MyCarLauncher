pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Mapbox SDK Maven Repository
        maven {
            url = uri("https://api.mapbox.com/downloads/v2/releases/maven")
            authentication {
                create<BasicAuthentication>("basic")
            }
            credentials {
                // Do not change Less Secure Password, it's always "mapbox"
                username = "mapbox"
                // Replace with your Mapbox Secret Downloads Token (starts with sk.eyJ...)
                password = providers.gradleProperty("MAPBOX_DOWNLOADS_TOKEN").getOrElse("sk.eyJ1IjoiYW43aDAiLCJhIjoiY212anU1bTRrMDM1MDJ5cHVmMWpoeHJieCJ9.Qs5yZmbMP15eK47mu-4kOw")
            }
        }
    }
}

rootProject.name = "OpenLauncher"
include(":app")
