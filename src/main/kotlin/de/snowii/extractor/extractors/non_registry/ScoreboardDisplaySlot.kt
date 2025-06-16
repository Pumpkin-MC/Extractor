package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import de.snowii.extractor.IExtractor
import net.minecraft.scoreboard.ScoreboardDisplaySlot
import net.minecraft.server.MinecraftServer

class ScoreboardDisplaySlot : IExtractor {
    override fun fileName(): String {
        return "scoreboard_display_slot.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val finalJson = JsonArray()
        for (slot in ScoreboardDisplaySlot.entries) {
            finalJson.add(
                slot.name,
            )
        }

        return finalJson
    }
}