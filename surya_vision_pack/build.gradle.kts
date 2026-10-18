plugins { id("com.android.ai-pack") }
aiPack {
    packName = "surya_vision_pack"
    dynamicDelivery { deliveryType = "install-time" }
}
tasks.configureEach { dependsOn(":app:prepareDistribution") }
