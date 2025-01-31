package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.Extractor
import net.minecraft.entity.data.TrackedDataHandlerRegistry
import net.minecraft.server.MinecraftServer


class MetaDataType : Extractor.Extractor {
    override fun fileName(): String {
        return "meta_data_types.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val jsonArray = JsonArray()
        val fields = TrackedDataHandlerRegistry::class.java.declaredFields

        for (field in fields) {
            if (field.name != "DATA_HANDLERS") {
                jsonArray.add(field.name)
            }
        }

        return jsonArray
    }
}