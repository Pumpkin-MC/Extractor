package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import de.snowii.extractor.Extractor
import net.minecraft.block.FlowerPotBlock
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer

class FlowerPotTransformation : Extractor.Extractor {
    override fun fileName(): String {
        return "flower_pot_transformations.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val flowerPotsJson = JsonObject()
        for ((block, pottedBlock) in FlowerPotBlock.CONTENT_TO_POTTED){
            if (Registries.BLOCK.getRawId(block) == 0) continue
            flowerPotsJson.add(
                Registries.ITEM.getRawId(block.asItem()!!).toString(),
                JsonPrimitive(Registries.BLOCK.getRawId(pottedBlock)))
        }

        return flowerPotsJson
    }
}