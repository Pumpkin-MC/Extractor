package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import de.snowii.extractor.Extractor
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.Item
import net.minecraft.world.level.storage.loot.LootContext
import net.minecraft.world.level.storage.loot.LootParams
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets
import java.util.Optional

class Fuels : Extractor.Extractor {
    override fun fileName(): String {
        return "fuels.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val fuelsJson = JsonObject()
        val lootContext = LootContext.Builder(
            LootParams.Builder(server.overworld()).create(LootContextParamSets.EMPTY)
        ).create(Optional.empty())

        BuiltInRegistries.ITEM.forEach { item ->
            val fuel = item.components().get(DataComponents.COOKING_FUEL) ?: return@forEach
            val burnTicks = fuel.burnTime().get(lootContext, 0)
            fuelsJson.add(Item.getId(item).toString(), JsonPrimitive(burnTicks))
        }
        return fuelsJson
    }
}
