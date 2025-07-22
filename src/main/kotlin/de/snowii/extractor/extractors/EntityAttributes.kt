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
            var subObject = JsonObject()
            subObject.addProperty("id", Registries.ATTRIBUTE.getRawId(attribute))
            subObject.addProperty("default_value", attribute.defaultValue)

            finalJson.add(
                Registries.ATTRIBUTE.getId(attribute)!!.path,
                subObject
            )
        }

        return finalJson
    }
}