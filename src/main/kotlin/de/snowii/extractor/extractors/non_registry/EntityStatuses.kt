package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.IExtractor
import net.minecraft.entity.EntityStatuses
import net.minecraft.server.MinecraftServer

class EntityStatuses : IExtractor {
    override fun fileName(): String {
        return "entity_statuses.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val jsonObject = JsonObject()
        val fields = EntityStatuses::class.java.declaredFields

        for (field in fields) {
            if (field.type == Byte::class.javaPrimitiveType || field.type == Byte::class.java) {
                if (field.name.startsWith("field")) continue
                field.isAccessible = true
                val byteValue = field.get(null) as Byte
                jsonObject.addProperty(field.name, byteValue)
            }
        }

        return jsonObject
    }
}