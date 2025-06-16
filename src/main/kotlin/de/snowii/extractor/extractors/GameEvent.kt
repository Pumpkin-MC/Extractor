package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.IExtractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer

class GameEvent : IExtractor {
    override fun fileName(): String {
        return "game_event.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val gameEventJson = JsonArray()
        val gameEventTypeRegistry =
            server.registryManager.getOrThrow(RegistryKeys.GAME_EVENT)
        for (event in gameEventTypeRegistry) {
            gameEventJson.add(
                gameEventTypeRegistry.getId(event)!!.path,
            )
        }

        return gameEventJson
    }
}