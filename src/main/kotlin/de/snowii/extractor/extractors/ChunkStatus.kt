package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.Extractor
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer

class ChunkStatus : Extractor.Extractor {
    override fun fileName(): String {
        return "chunk_status.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val statusJson = JsonArray()
        for (status in Registries.CHUNK_STATUS) {
            statusJson.add(
                Registries.CHUNK_STATUS.getId(status).path,
            )
        }

        return statusJson
    }
}