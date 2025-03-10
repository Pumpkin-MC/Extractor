package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.registry.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings

class ChunkGenSetting : Extractor.Extractor {
    override fun fileName(): String {
        return "chunk_gen_settings.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val finalJson = JsonObject()
        val registry =
            server.registryManager.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS)
        for (setting in registry) {
            finalJson.add(
                registry.getId(setting)!!.path,
                ChunkGeneratorSettings.CODEC.encodeStart(
                    RegistryOps.of(JsonOps.INSTANCE, server.registryManager),
                    setting
                ).getOrThrow()
            )
        }

        return finalJson
    }
}