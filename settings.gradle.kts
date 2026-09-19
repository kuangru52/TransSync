// Clear ANDROID_PREFS_ROOT if ANDROID_USER_HOME is also set to prevent AGP AndroidLocationsException
if (System.getenv("ANDROID_PREFS_ROOT") != null && System.getenv("ANDROID_USER_HOME") != null) {
    try {
        val peClass = Class.forName("java.lang.ProcessEnvironment")
        val envField = peClass.getDeclaredField("theEnvironment")
        envField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val envMap = envField.get(null) as MutableMap<String, String>
        envMap.remove("ANDROID_PREFS_ROOT")

        val ciEnvField = peClass.getDeclaredField("theCaseInsensitiveEnvironment")
        ciEnvField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val ciEnvMap = ciEnvField.get(null) as MutableMap<String, String>
        ciEnvMap.remove("ANDROID_PREFS_ROOT")
    } catch (_: Throwable) {
        System.clearProperty("ANDROID_PREFS_ROOT")
    }
}

pluginManagement {
    repositories {
        google()
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
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "TransSync"
include(":app")
