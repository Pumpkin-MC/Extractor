package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.material.MapColor
import java.lang.reflect.Modifier

class MapColors : Extractor.Extractor {
    override fun fileName(): String {
        return "map_colors.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val array = JsonArray()

        val fieldMap = mutableMapOf<Int, String>()
        for (field in MapColor::class.java.declaredFields) {
            if (Modifier.isStatic(field.modifiers) && MapColor::class.java.isAssignableFrom(field.type)) {
                field.isAccessible = true
                val mapColor = field.get(null) as? MapColor
                if (mapColor != null) {
                    fieldMap[mapColor.id] = field.name.lowercase()
                }
            }
        }

        for (id in 0..63) {
            val mapColor = MapColor.byId(id)
            val json = JsonObject()
            json.addProperty("id", id)
            json.addProperty("name", fieldMap[id] ?: "none")
            json.addProperty("col", mapColor.col)
            val hex = String.format("#%06X", mapColor.col)
            json.addProperty("hex", hex)

            val rgb = JsonArray()
            rgb.add((mapColor.col shr 16) and 0xFF)
            rgb.add((mapColor.col shr 8) and 0xFF)
            rgb.add(mapColor.col and 0xFF)
            json.add("rgb", rgb)

            array.add(json)
        }

        return array
    }
}
