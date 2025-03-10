package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.world.gen.carver.CarverConfig
import net.minecraft.world.gen.carver.ConfiguredCarver
import net.minecraft.world.gen.feature.ConfiguredFeature

class Carver : Extractor.Extractor {
    override fun fileName(): String {
        return "carver.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val finalJson = JsonObject()
        val registry =
            server.registryManager.getOrThrow(RegistryKeys.CONFIGURED_CARVER)
        for (setting in registry) {
            finalJson.add(
                registry.getId(setting)!!.path,
                ConfiguredCarver.CODEC.encodeStart(
                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                    setting
                ).getOrThrow()
            )
        }

        return finalJson
    }
}