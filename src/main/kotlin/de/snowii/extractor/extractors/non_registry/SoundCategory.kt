package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.IExtractor
import net.minecraft.server.MinecraftServer
import net.minecraft.sound.SoundCategory

class SoundCategory : IExtractor {
    override fun fileName(): String {
        return "sound_category.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val categoriesJson = JsonArray()
        for (category in SoundCategory.entries) {
            categoriesJson.add(
                category.name,
            )
        }

        return categoriesJson
    }
}