package de.snowii.extractor.extractors.non_registry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import de.snowii.extractor.Extractor
import net.minecraft.block.FlowerPotBlock
import net.minecraft.registry.Registries
import net.minecraft.server.MinecraftServer

class FlowerPot : Extractor.Extractor {
    override fun fileName(): String {
        return "flower_pot.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val flowerPotsJson = JsonObject()
        for ((block, pottedBlock) in FlowerPotBlock.CONTENT_TO_POTTED){
            flowerPotsJson.addProperty(
                Registries.BLOCK.getId(block).path,
                Registries.BLOCK.getId(pottedBlock).path)
        }

        return flowerPotsJson
    }
}