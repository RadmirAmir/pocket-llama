pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
    resolutionStrategy.eachPlugin {
        if (requested.id.id.startsWith("com.android.")) useModule("com.android.tools.build:gradle:${requested.version}")
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "PocketLlama"
include(":app")
if (providers.gradleProperty("pocket.channel").orElse("community").get() == "play") {
    include(":speech_pack", ":ocr_pack")
    if (providers.gradleProperty("pocket.withSurya").orElse("false").get().toBoolean()) {
        include(":surya_pack", ":surya_vision_pack")
    }
}
