package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import de.snowii.extractor.Extractor
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.registry.Registries
import net.minecraft.registry.RegistryKeys
import net.minecraft.server.MinecraftServer

class RecipeRemainder : Extractor.Extractor  {
    override fun fileName(): String {
        return "recipe_remainder.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val recipeRemainderJson = JsonObject()

        for (item in server.registryManager.getOrThrow(RegistryKeys.ITEM).streamEntries().toList()) {
            val realItem: Item = item.value()
            val remainder = realItem.recipeRemainder;
            if (remainder == ItemStack.EMPTY) {
                continue
            }


            recipeRemainderJson.add(
                Registries.ITEM.getRawId(realItem).toString(),
                JsonPrimitive(Registries.ITEM.getRawId(remainder.item).toString()),
            )

        }
        return recipeRemainderJson
    }
}