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
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()   // for com.example:ssoapi published from sso-api-lib
        google()
        mavenCentral()
    }
}

rootProject.name = "account"
include(":app")
// ssoapi is now a standalone library — import via implementation("com.example:ssoapi:1.0.0")
