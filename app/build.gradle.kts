import groovy.json.JsonSlurper
plugins { id("com.android.application") }
val channel = providers.gradleProperty("pocket.channel").orElse("community").get()
require(channel in listOf("personal", "community", "play"))
val withSurya = channel == "personal" || providers.gradleProperty("pocket.withSurya").orElse("false").get().toBoolean()
val releaseInfo = JsonSlurper().parse(rootProject.file("distribution/release.json")) as Map<*, *>
val signingFile = providers.environmentVariable("POCKET_KEYSTORE").orNull
android {
    namespace = "dev.pocketllama"
    compileSdk = 36
    buildToolsVersion = "36.0.0"
    defaultConfig {
        applicationId = releaseInfo[if (channel == "personal") "legacyApplicationId" else "applicationId"] as String
        minSdk = 29
        targetSdk = 36
        versionCode = (releaseInfo["versionCode"] as Number).toInt()
        versionName = releaseInfo["versionName"] as String
        ndk { abiFilters += "arm64-v8a" }
    }
    if (signingFile != null) {
        signingConfigs.create("publisher") {
            storeFile = file(signingFile)
            storePassword = providers.environmentVariable("POCKET_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("POCKET_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("POCKET_KEY_PASSWORD").get()
        }
    }
    buildTypes { release { isMinifyEnabled = false; if (signingFile != null) signingConfig = signingConfigs.getByName("publisher") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    sourceSets.getByName("main").assets.setSrcDirs(listOf(rootProject.file("build/distribution/$channel/assets")))
    packaging.jniLibs {
        useLegacyPackaging = true
        keepDebugSymbols += "**/libllama_*.so"
        keepDebugSymbols += "**/libpocket_vulkan.so"
    }
    if (channel == "play") {
        assetPacks += listOf(":speech_pack", ":ocr_pack")
        if (withSurya) assetPacks += listOf(":surya_pack", ":surya_vision_pack")
    }
}
val prepareDistribution by tasks.registering(Exec::class) {
    workingDir = rootProject.projectDir
    commandLine(listOf("python3", "scripts/prepare-distribution.py", "--channel", channel) + if (withSurya) listOf("--with-surya") else emptyList())
}
tasks.named("preBuild") { dependsOn(prepareDistribution) }
dependencies {
    implementation(files("libs/opencv-4.12.0.jar", "libs/jsoup-1.21.2.jar", "libs/onnxruntime-android-1.23.2.jar", "libs/sqlcipher-android-4.19.1.jar", "libs/androidx-sqlite-2.7.0.jar", "libs/kotlin-stdlib-2.2.10.jar"))
}
