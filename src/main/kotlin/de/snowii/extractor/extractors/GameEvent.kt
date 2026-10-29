package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer

class GameEvent : Extractor.Extractor {
    override fun fileName(): String {
        return "game_event.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val gameEventJson = JsonArray()
        val registry =
            server.registryAccess().lookupOrThrow(Registries.GAME_EVENT)
        for (event in registry) {
            val eventJson = JsonObject()
            eventJson.addProperty("name", registry.getKey(event)!!.path)
            eventJson.addProperty("notification_radius", event.notificationRadius())
            gameEventJson.add(eventJson)
        }

        return gameEventJson
    }
}