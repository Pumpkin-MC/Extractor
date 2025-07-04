package de.snowii.extractor.extractors

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import de.snowii.extractor.Extractor
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.server.MinecraftServer



class Fuels : Extractor.Extractor {
    override fun fileName(): String {
        return "fuels.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val fuelsJson = JsonObject()
        server.fuelRegistry.fuelItems.forEach { fuel ->
            fuelsJson.add(Item.getRawId(fuel).toString(), JsonPrimitive(server.fuelRegistry.getFuelTicks(ItemStack(fuel))))
        }
        return fuelsJson
    }
}