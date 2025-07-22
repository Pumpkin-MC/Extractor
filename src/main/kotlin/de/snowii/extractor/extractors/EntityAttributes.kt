package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer

class EntityAttributes : Extractor.Extractor {
    override fun fileName(): String {
        return "attributes.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val finalJson = JsonObject()
        for (attribute in Registries.ATTRIBUTE) {
            finalJson.addProperty(
                Registries.ATTRIBUTE.getId(attribute)!!.path,
                attribute.defaultValue
            )
        }

        return finalJson
    }
}