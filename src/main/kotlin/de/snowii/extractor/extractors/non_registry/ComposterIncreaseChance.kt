package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt

class ComposterIncreaseChance : Extractor.Extractor {
    override fun fileName(): String {
        return "composter_increase_chance.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val composterChancesJson = JsonObject()
        val ops = server.registryAccess().createSerializationContext(JsonOps.INSTANCE)
        BuiltInRegistries.ITEM.forEach { item ->
            val compostable = item.components().get(DataComponents.COMPOSTABLE) ?: return@forEach
            val encoded = ResolvableInt.CODEC.encodeStart(ops, compostable.layers()).getOrThrow()
            composterChancesJson.add(
                BuiltInRegistries.ITEM.getId(item).toString(),
                JsonPrimitive(chanceFromEncoded(encoded))
            )
        }
        return composterChancesJson
    }

    private fun chanceFromEncoded(el: JsonElement): Float {
        if (el.isJsonPrimitive) {
            val primitive = el.asJsonPrimitive
            if (primitive.isNumber) {
                return if (primitive.asInt >= 1) 1.0f else 0.0f
            }
            if (primitive.isString) {
                return when (Identifier.parse(primitive.asString).path) {
                    "compostable/low" -> 0.3f
                    "compostable/low_medium" -> 0.5f
                    "compostable/medium" -> 0.65f
                    "compostable/medium_high" -> 0.85f
                    "compostable/always_add_one" -> 1.0f
                    else -> error("Unknown compostable provider ${primitive.asString}")
                }
            }
        }
        if (el.isJsonObject) {
            val obj = el.asJsonObject
            if (obj.has("distribution")) {
                var oneWeight = 0
                var totalWeight = 0
                for (entry in obj.getAsJsonArray("distribution")) {
                    val row = entry.asJsonObject
                    val weight = row.get("weight").asInt
                    totalWeight += weight
                    if (row.get("data").asInt == 1) {
                        oneWeight += weight
                    }
                }
                return oneWeight.toFloat() / totalWeight.toFloat()
            }
            if (obj.has("default")) {
                return chanceFromEncoded(obj.get("default"))
            }
        }
        error("Cannot derive compost chance from $el")
    }
}
