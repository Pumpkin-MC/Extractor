package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.mojang.serialization.DynamicOps
import com.mojang.serialization.JsonOps
import de.snowii.extractor.Extractor
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.loot.providers.number.ints.ContextIntProviders
import net.minecraft.world.level.storage.loot.providers.number.ints.ResolvableInt

class ComposterIncreaseChance : Extractor.Extractor {
    override fun fileName(): String {
        return "composter_increase_chance.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val composterChancesJson = JsonObject()
        val ops = server.reloadableRegistries().lookup().createSerializationContext(JsonOps.INSTANCE)
        BuiltInRegistries.ITEM.forEach { item ->
            val compostable = item.components().get(DataComponents.COMPOSTABLE) ?: return@forEach
            val encoded = ResolvableInt.CODEC.encodeStart(ops, compostable.layers()).getOrThrow()
            composterChancesJson.add(
                BuiltInRegistries.ITEM.getId(item).toString(),
                JsonPrimitive(chanceFromEncoded(encoded, ops))
            )
        }
        return composterChancesJson
    }

    private fun chanceFromEncoded(el: JsonElement, ops: DynamicOps<JsonElement>): Float {
        if (el.isJsonPrimitive) {
            val primitive = el.asJsonPrimitive
            if (primitive.isNumber) {
                return if (primitive.asInt >= 1) 1.0f else 0.0f
            }
            if (primitive.isString) {
                val provider = ContextIntProviders.CODEC.parse(ops, primitive).getOrThrow().value()
                return chanceFromEncoded(ContextIntProviders.DIRECT_CODEC.encodeStart(ops, provider).getOrThrow(), ops)
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
                return chanceFromEncoded(obj.get("default"), ops)
            }
        }
        error("Cannot derive compost chance from $el")
    }
}
