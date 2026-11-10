package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.ai.attributes.RangedAttribute

class EntityAttributes : Extractor.Extractor {
    override fun fileName(): String {
        return "attributes.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val finalJson = JsonObject()
        for (attribute in BuiltInRegistries.ATTRIBUTE) {
            val subObject = JsonObject()
            subObject.addProperty("id", BuiltInRegistries.ATTRIBUTE.getId(attribute))
            subObject.addProperty("default_value", attribute.defaultValue)
            if (attribute is RangedAttribute) {
                subObject.addProperty("min_value", attribute.minValue)
                subObject.addProperty("max_value", attribute.maxValue)
            }
            subObject.addProperty("client_syncable", attribute.isClientSyncable)

            finalJson.add(
                BuiltInRegistries.ATTRIBUTE.getKey(attribute)!!.path,
                subObject
            )
        }

        return finalJson
    }
}