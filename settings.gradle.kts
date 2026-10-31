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

rootProject.name = "android-on-device-llm"

include(":app")

// The inference engine: the llama-kotlin library, pulled in as a git submodule.
include(":llama")
project(":llama").projectDir = file("third_party/llama-kotlin/llama")
