package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.Extractor
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer

class StatusEffects : Extractor.Extractor {
    override fun fileName(): String {
        return "status_effects.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val statusJson = JsonArray()
        for (status in Registries.STATUS_EFFECT) {
            println(status.translationKey)
            statusJson.add(
                Registries.STATUS_EFFECT.getId(status)!!.path,
            )
        }

        return statusJson
    }
}