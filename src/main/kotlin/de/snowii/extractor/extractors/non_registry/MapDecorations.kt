package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer

class MapDecorations : Extractor.Extractor {
    override fun fileName(): String {
        return "map_decorations.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val array = JsonArray()
        for (type in BuiltInRegistries.MAP_DECORATION_TYPE) {
            val key = BuiltInRegistries.MAP_DECORATION_TYPE.getKey(type) ?: continue
            val json = JsonObject()
            json.addProperty("name", key.path)
            json.addProperty("asset_name", type.assetId().path)
            json.addProperty("show_on_item_frame", type.showOnItemFrame())
            json.addProperty("map_color", type.mapColor())
            json.addProperty("exploration_map_element", type.explorationMapElement())
            json.addProperty("track_count", type.trackCount())
            array.add(json)
        }
        return array
    }
}
