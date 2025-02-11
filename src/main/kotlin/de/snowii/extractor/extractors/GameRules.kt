package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.resource.featuretoggle.FeatureFlags
import net.minecraft.server.MinecraftServer
import net.minecraft.world.GameRules

class GameRules : Extractor.Extractor {
    override fun fileName(): String {
        return "game_rules.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val gameEventJson = JsonObject()
        val rules = GameRules(FeatureFlags::VANILLA_FEATURES.get())
        rules.accept(object : GameRules.Visitor {
            override fun <T : GameRules.Rule<T>?> visit(key: GameRules.Key<T>, type: GameRules.Type<T>) {
                gameEventJson.addProperty(key.name, type.createRule()!!.serialize())
            }
        })
        return gameEventJson
    }
}