package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer

class DataComponent : Extractor.Extractor {
    override fun fileName(): String = "data_component.json"

    override fun extract(server: MinecraftServer): JsonElement {
        val registry = BuiltInRegistries.DATA_COMPONENT_TYPE
        val json = JsonObject()

        for ((key, type) in registry.entrySet()) {
            val properties = JsonObject().apply {
                addProperty("id", registry.getId(type))
                addProperty("persistent", !type.isTransient)
                addProperty("ignore_swap_animation", type.ignoreSwapAnimation())
            }
            json.add(key.identifier().toString(), properties)
        }

        return json
    }
}
