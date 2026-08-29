package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.DyeColor

class DyeColors : Extractor.Extractor {
    override fun fileName(): String {
        return "dye_colors.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val array = JsonArray()
        for (color in DyeColor.entries) {
            val json = JsonObject()
            json.addProperty("id", color.id)
            json.addProperty("name", color.getName())
            json.addProperty("map_color_id", color.mapColor.id)
            json.addProperty("terracotta_color_id", color.terracottaColor.id)
            json.addProperty("texture_diffuse_color", color.textureDiffuseColor and 0xFFFFFF)
            json.addProperty("firework_color", color.fireworkColor and 0xFFFFFF)
            json.addProperty("text_color", color.textColor and 0xFFFFFF)
            array.add(json)
        }
        return array
    }
}
