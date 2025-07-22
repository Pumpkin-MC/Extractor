package de.snowii.extractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.potion.Potion
import net.minecraft.recipe.Ingredient
import net.minecraft.registry.Registries
import net.minecraft.registry.entry.RegistryEntry
import net.minecraft.server.MinecraftServer


class PotionBrewing : Extractor.Extractor {
    override fun fileName(): String {
        return "potion_brewing.json"
    }


    override fun extract(server: MinecraftServer): JsonElement {
        val json = JsonObject()
        val reg = server.brewingRecipeRegistry

        val t = reg.javaClass.getDeclaredField("potionTypes")
        t.isAccessible = true
        val potionTypes = t.get(reg) as? List<Ingredient>
        val types = JsonArray()
        for (type in potionTypes!!) {
            val items = JsonArray()
            for (item in type.getMatchingItems()) {
                items.add(item.value().toString())
            }
            types.add(items)
        }
        json.add("potion_types", types)

        val t2 = reg.javaClass.getDeclaredField("potionRecipes")
        t2.isAccessible = true
        val potionRecipes = t2.get(reg) as? List<*>
        val recipes = JsonArray()
        for (recipe in potionRecipes!!) {
            val recipeJson = JsonObject()
            val clazz = recipe?.javaClass
            clazz?.let {
                for (field in it.declaredFields) {
                    field.isAccessible = true
                    val value = field.get(recipe)
                    if (value is RegistryEntry<*>) {
                        recipeJson.addProperty(field.name, value.key.get().value.toString())
                    } else if (value is Ingredient) {
                        val tags = JsonArray()
                        for (tag in value.getMatchingItems()) {
                            tags.add(tag.value().toString())
                        }
                        recipeJson.add(field.name, tags)
                    }
                }
            }
            recipes.add(recipeJson)
        }
        json.add("potion_recipes", recipes)

        val t3 = reg.javaClass.getDeclaredField("itemRecipes")
        t3.isAccessible = true
        val itemRecipes = t3.get(reg) as? List<*>
        val recipes2 = JsonArray()
        for (recipe in itemRecipes!!) {
            val recipeJson = JsonObject()
            val clazz = recipe?.javaClass
            clazz?.let {
                for (field in it.declaredFields) {
                    field.isAccessible = true
                    val value = field.get(recipe)
                    if (value is RegistryEntry<*>) {
                        recipeJson.addProperty(field.name, value.key.get().value.toString())
                    } else if (value is Ingredient) {
                        val tags = JsonArray()
                        for (tag in value.getMatchingItems()) {
                            tags.add(tag.value().toString())
                        }
                        recipeJson.add(field.name, tags)
                    }
                }
            }
            recipes2.add(recipeJson)
        }
        json.add("item_recipes", recipes2)

        return json
    }
}
