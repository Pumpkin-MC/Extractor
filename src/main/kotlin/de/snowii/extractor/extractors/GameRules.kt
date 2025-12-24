package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer
import net.minecraft.world.rule.GameRuleType

class GameRules : Extractor.Extractor {
    override fun fileName(): String {
        return "game_rules.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val gameEventJson = JsonObject()
        val gameEventTypeRegistry =
            server.registryManager.getOrThrow(RegistryKeys.GAME_RULE)
        for (rule in gameEventTypeRegistry) {
            when (rule.type) {
                GameRuleType.INT -> gameEventJson.addProperty(rule.toString(), rule.defaultValue as Int)
                GameRuleType.BOOL -> gameEventJson.addProperty(rule.toString(), rule.defaultValue as Boolean)
            }
        }
        return gameEventJson
    }
}