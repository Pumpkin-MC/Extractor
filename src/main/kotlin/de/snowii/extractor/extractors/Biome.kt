package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer

class Biome : Extractor.Extractor {
    override fun fileName(): String {
        return "biome.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val biomesJson = JsonArray()
        val biomeRegistry =
            server.registryManager.getOrThrow(RegistryKeys.BIOME)
        for (biome in biomeRegistry) {
            val biomeData = JsonObject()
            biomeData.addProperty("name", biomeRegistry.getId(biome)!!.path)
            biomeData.addProperty("id", biomeRegistry.getRawId(biome))

            biomesJson.add(biomeData)
        }

        return biomesJson
    }
}