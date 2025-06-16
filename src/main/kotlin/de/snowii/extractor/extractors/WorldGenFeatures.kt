package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.IExtractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.world.gen.feature.ConfiguredFeature

class WorldGenFeatures : IExtractor {
    override fun fileName(): String {
        return "gen_features.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val finalJson = JsonObject()
        val registry =
            server.registryManager.getOrThrow(RegistryKeys.CONFIGURED_FEATURE)
        for (setting in registry) {
            finalJson.add(
                registry.getId(setting)!!.path,
                ConfiguredFeature.CODEC.encodeStart(
                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                    setting
                ).getOrThrow()
            )
        }

        return finalJson
    }
}