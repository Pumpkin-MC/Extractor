package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.entity.data.TrackedDataHandler
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.server.MinecraftServer
import java.lang.reflect.Modifier


class MetaDataType : Extractor.Extractor {
    override fun fileName(): String {
        return "meta_data_type.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val jsonObject = JsonObject()

        for (field in TrackedDataHandlerRegistry::class.java.declaredFields) {
            if (java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                TrackedDataHandler::class.java.isAssignableFrom(field.type)) {

                try {
                    field.isAccessible = true
                    val handler = field.get(null) as TrackedDataHandler<*>

                    val id = TrackedDataHandlerRegistry.getId(handler)

                    if (id != -1) {
                        jsonObject.addProperty(field.name.lowercase(), id)
                    }
                } catch (e: Exception) {
                }
            }
        }

        return jsonObject
    }
}