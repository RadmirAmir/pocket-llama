plugins { id("com.android.ai-pack") }
aiPack {
    packName = "ocr_pack"
    dynamicDelivery { deliveryType = "install-time" }
}
tasks.configureEach { dependsOn(":app:prepareDistribution") }
