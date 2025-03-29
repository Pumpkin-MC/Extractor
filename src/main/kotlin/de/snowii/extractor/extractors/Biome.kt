package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.world.biome.Biome

class Biome : Extractor.Extractor {
    override fun fileName(): String {
        return "biome.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val biomeData = JsonObject()
        val biomeRegistry =
            server.registryManager.getOrThrow(RegistryKeys.BIOME)
        for (biome in biomeRegistry) {
            biomeData.add(
                biomeRegistry.getId(biome)!!.path, Biome.CODEC.encodeStart(
                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                    biome
                ).getOrThrow()
            )

        }

        return biomeData
    }
}