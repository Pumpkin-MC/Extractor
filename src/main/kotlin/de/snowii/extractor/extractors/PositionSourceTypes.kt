package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer

class PositionSourceTypes : Extractor.Extractor {
    override fun fileName(): String {
        return "position_source_type.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val positionSourceTypesJson = JsonArray()
        for (type in BuiltInRegistries.POSITION_SOURCE_TYPE) {
            positionSourceTypesJson.add(
                BuiltInRegistries.POSITION_SOURCE_TYPE.getKey(type)!!.path,
            )
        }

        return positionSourceTypesJson
    }
}
