package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.world.dimension.DimensionType

class Dimension : Extractor.Extractor {
    override fun fileName(): String {
        return "dimension.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val finalJson = JsonObject()
        val registry =
            server.registryManager.getOrThrow(RegistryKeys.DIMENSION_TYPE)
        for (setting in registry) {
            finalJson.add(
                registry.getId(setting)!!.toString(),
                DimensionType.CODEC.encodeStart(
                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                    setting
                ).getOrThrow()
            )
        }

        return finalJson
    }
}