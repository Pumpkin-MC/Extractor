package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.server.MinecraftServer
import net.minecraft.world.inventory.SlotRanges

class SlotRanges : Extractor.Extractor {
    override fun fileName(): String {
        return "slot_ranges.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val slotRanges = JsonObject()
        for (slotRangeName in SlotRanges.allNames()) {
            val slotRange = SlotRanges.nameToIds(slotRangeName)!!
            val intList = slotRange.slots()

            val intArray = JsonArray()
            intList.forEach { i -> intArray.add(i) }

            slotRanges.add(slotRangeName, intArray)
        }
        return slotRanges
    }
}