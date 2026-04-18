package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.brigadier.arguments.IntegerArgumentType
import de.snowii.extractor.Extractor
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.gamerules.GameRuleType

class GameRules : Extractor.Extractor {
    override fun fileName(): String {
        return "game_rules.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val gameEventJson = JsonObject()
        val registry =
            server.registryAccess().lookupOrThrow(Registries.GAME_RULE)
        for (rule in registry) {
            when (rule.gameRuleType()) {
                GameRuleType.INT -> {
                    val intValues = JsonObject()
                    intValues.addProperty("default", rule.defaultValue() as Int)
                    val argument = rule.argument()
                    if (argument is IntegerArgumentType) {
                        intValues.addProperty("min", argument.minimum)
                        intValues.addProperty("max", argument.maximum)
                    }
                    gameEventJson.add(rule.toString(), intValues)
                }
                GameRuleType.BOOL -> gameEventJson.addProperty(rule.toString(), rule.defaultValue() as Boolean)
            }
        }
        return gameEventJson
    }
}