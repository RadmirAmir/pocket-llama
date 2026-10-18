plugins { id("com.android.ai-pack") }
aiPack {
    packName = "speech_pack"
    dynamicDelivery { deliveryType = "install-time" }
}
tasks.configureEach { dependsOn(":app:prepareDistribution") }
