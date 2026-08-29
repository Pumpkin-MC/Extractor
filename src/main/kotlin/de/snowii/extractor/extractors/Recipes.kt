package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.crafting.Recipe

class Recipes : Extractor.Extractor {
    override fun fileName(): String {
        return "recipes.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val recipesJson = JsonObject()

        for (recipeRaw in server.recipeManager.recipes) {
            val recipe = recipeRaw.value
            recipesJson.add(
                recipeRaw.id.identifier().toString(),
                Recipe.CODEC.encodeStart(
                    JsonOps.INSTANCE,
                    recipe
                ).getOrThrow()
            )
        }
        return recipesJson
    }
}
